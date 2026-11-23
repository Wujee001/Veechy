package com.lingxi.ai.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.lingxi.ai.MainActivity
import com.lingxi.ai.R
import com.lingxi.ai.pet.chibi.ChibiView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 桌面小 Veechy：1.2 cm 高的三维小人。
 *
 * 人物由 ChibiView 程序化建模并实时渲染（骨骼 + 三维投影 + 体积光照），
 * 自己会走、跳、坐、躺、挥手；点她会互动。
 * 每隔 1–3 小时主动说一句话：这句话走模型（人设 + 真实时间 + 当前对话历史），
 * 显示在紫蓝气泡里，同时并入当前对话。
 */
class PetService : Service() {

    companion object {
        const val ACTION_STOP = "com.lingxi.ai.pet.STOP"
        const val ACTION_RELOAD = "com.lingxi.ai.pet.RELOAD"
    }

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private lateinit var chibi: ChibiView
    private lateinit var bubble: TextView
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hideBubble: Runnable? = null

    private fun mm(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_MM, v, resources.displayMetrics
    ).toInt()

    private val msgRunnable = object : Runnable {
        override fun run() {
            if (PetPrefs.enabled(this@PetService) && PetPrefs.messagesEnabled(this@PetService)) {
                scope.launch {
                    val text = PetLineGenerator.generate(this@PetService)
                    if (text.isNotBlank()) {
                        showBubble(text, 16000)
                        PetOutbox.push(this@PetService, text)
                        PetBus.emit()
                        chibi.wave()
                    }
                }
            }
            handler.postDelayed(this, nextDelay())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ServiceCompat.startForeground(
            this,
            1,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                PetPrefs.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RELOAD -> {
                handler.removeCallbacks(msgRunnable)
                handler.postDelayed(msgRunnable, nextDelay())
            }
        }

        if (!Settings.canDrawOverlays(this) || !PetPrefs.enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (root == null) {
            attach()
            handler.postDelayed(msgRunnable, nextDelay())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ------------------------------------------------------------ 悬浮窗

    private fun attach() {
        val metrics = resources.displayMetrics

        bubble = TextView(this).apply {
            visibility = View.GONE
            setTextColor(AColor.WHITE)
            textSize = 12.5f
            maxWidth = mm(38f)
            setPadding(mm(3f), mm(2f), mm(3f), mm(2f))
            background = GradientDrawable().apply {
                cornerRadius = mm(4f).toFloat()
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(0xE07C5CFF.toInt(), 0xE04DA3FF.toInt())
                setStroke(1, 0x66FFFFFF)
            }
            elevation = 8f
        }

        chibi = ChibiView(this).apply {
            layoutParams = LinearLayout.LayoutParams(mm(26f), mm(19f))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(chibi)
            addView(bubble)
        }

        params = WindowManager.LayoutParams(
            mm(40f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = PetPrefs.position(this@PetService)
            x = saved?.first ?: (metrics.widthPixels - mm(40f))
            y = saved?.second ?: (metrics.heightPixels / 3)
        }

        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                react()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                openApp()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                showBubble(PetMessages.longPress(this@PetService))
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                params.x -= distanceX.toInt()
                params.y -= distanceY.toInt()
                clamp()
                runCatching { wm.updateViewLayout(container, params) }
                return true
            }
        })

        container.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    PetPrefs.savePosition(this, params.x, params.y)
            }
            gesture.onTouchEvent(ev)
            true
        }

        runCatching { wm.addView(container, params) }
        root = container
        clamp()
        runCatching { wm.updateViewLayout(container, params) }
    }

    private fun clamp() {
        val m = resources.displayMetrics
        params.x = params.x.coerceIn(0, (m.widthPixels - mm(40f)).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (m.heightPixels - mm(24f)).coerceAtLeast(0))
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }
    }

    // ------------------------------------------------------------ 互动

    private fun react() {
        chibi.poke()
        showBubble(PetMessages.tap(this), 5000)
    }

    private fun showBubble(text: String, holdMs: Long = 7000) {
        if (text.isBlank()) return
        bubble.text = text
        bubble.visibility = View.VISIBLE
        bubble.alpha = 0f
        bubble.animate().alpha(1f).setDuration(180).start()
        hideBubble?.let { handler.removeCallbacks(it) }
        val hide = Runnable {
            bubble.animate().alpha(0f).setDuration(220).withEndAction {
                bubble.visibility = View.GONE
            }.start()
        }
        hideBubble = hide
        handler.postDelayed(hide, holdMs)
    }

    private fun nextDelay(): Long = Random.nextLong(60, 181) * 60_000L

    // ------------------------------------------------------------ 通知

    private fun buildNotification(): Notification {
        val channelId = "vee_pet"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "桌面小 Veechy", NotificationManager.IMPORTANCE_LOW).apply {
                description = "桌宠与定时留言"
                setShowBadge(false)
            }
        )

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val close = PendingIntent.getService(
            this, 1,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, channelId)
            .setContentTitle("Veechy 在桌面上")
            .setContentText("点她一下试试")
            .setSmallIcon(R.drawable.ic_pet_notify)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_pet_notify),
                    "关闭桌宠",
                    close
                ).build()
            )
            .setOngoing(true)
            .build()
    }
}