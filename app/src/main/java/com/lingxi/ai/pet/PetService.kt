package com.lingxi.ai.pet

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.lingxi.ai.MainActivity
import com.lingxi.ai.R
import kotlin.random.Random

/**
 * 桌面小 Veechy。
 *
 * 一个悬浮窗 + 一套行为链：呼吸、换姿势、歪头、眨眼、举手、打盹，
 * 点她会有反应；隔 1–3 小时冒一句符合人设的话，并写进待办队列，
 * 用户打开 App 后这句话会出现在当前对话里。
 *
 * 受系统限制：需要「显示在其他应用上层」权限；进程被厂商杀掉后需要重新打开 App。
 */
class PetService : Service() {

    companion object {
        const val ACTION_STOP = "com.lingxi.ai.pet.STOP"
        const val ACTION_RELOAD = "com.lingxi.ai.pet.RELOAD"
    }

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private lateinit var pet: ImageView
    private lateinit var bubble: TextView
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var bob: ObjectAnimator? = null
    private var hideBubble: Runnable? = null
    private var poseRes = R.drawable.pet_idle

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val idleRunnable = object : Runnable {
        override fun run() {
            idleAction()
            handler.postDelayed(this, Random.nextLong(4200, 9000))
        }
    }

    private val msgRunnable = object : Runnable {
        override fun run() {
            if (PetPrefs.enabled(this@PetService) && PetPrefs.messagesEnabled(this@PetService)) {
                val text = runCatching { PetMessages.scheduled(this@PetService) }.getOrDefault("")
                if (text.isNotBlank()) {
                    showBubble(text, 15000)
                    PetOutbox.push(this@PetService, text)
                    PetBus.emit()
                    pose(R.drawable.pet_wave)
                    handler.postDelayed({ pose(restPose()) }, 2600)
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
            handler.postDelayed(idleRunnable, 1200)
            handler.postDelayed(msgRunnable, nextDelay())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bob?.cancel()
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
            maxWidth = dp(210)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(0xE07C5CFF.toInt(), 0xE04DA3FF.toInt())
                setStroke(dp(1), 0x66FFFFFF)
            }
            elevation = dp(8).toFloat()
            setLineSpacing(dp(2).toFloat(), 1f)
        }

        pet = ImageView(this).apply {
            setImageResource(R.drawable.pet_idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(132), dp(157))
            elevation = dp(6).toFloat()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(bubble)
            addView(pet)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = PetPrefs.position(this@PetService)
            x = saved?.first ?: (metrics.widthPixels - dp(150))
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
        startBob()
        pose(restPose())
    }

    private fun clamp() {
        val m = resources.displayMetrics
        params.x = params.x.coerceIn(0, (m.widthPixels - dp(140)).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (m.heightPixels - dp(180)).coerceAtLeast(0))
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

    // ------------------------------------------------------------ 行为

    private fun isNight(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 23 || h <= 5
    }

    private fun restPose(): Int = if (isNight()) R.drawable.pet_rest else R.drawable.pet_idle

    private fun startBob() {
        bob?.cancel()
        bob = ObjectAnimator.ofFloat(pet, "translationY", 0f, -dp(7).toFloat()).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun pose(res: Int) {
        if (res == poseRes) return
        poseRes = res
        pet.animate().cancel()
        pet.animate().alpha(0f).setDuration(110).withEndAction {
            pet.setImageResource(res)
            pet.animate().alpha(1f).setDuration(170).start()
        }.start()
    }

    private fun idleAction() {
        when (Random.nextInt(8)) {
            0, 1 -> pose(restPose())
            2 -> pose(R.drawable.pet_read)
            3 -> pose(R.drawable.pet_tech)
            4 -> tilt()
            5 -> blink()
            6 -> pose(if (isNight()) R.drawable.pet_dream else R.drawable.pet_wave)
            else -> pose(R.drawable.pet_idle)
        }
        if (isNight() && pet.alpha > 0.9f) {
            pet.animate().alpha(0.82f).setDuration(800).start()
        } else if (!isNight() && pet.alpha < 0.95f) {
            pet.animate().alpha(1f).setDuration(500).start()
        }
    }

    private fun tilt() {
        val angle = if (Random.nextBoolean()) 4f else -4f
        pet.animate().rotation(angle).setDuration(600).withEndAction {
            handler.postDelayed({
                pet.animate().rotation(0f).setDuration(700).start()
            }, 900)
        }.start()
    }

    private fun blink() {
        val current = poseRes
        if (current == R.drawable.pet_dream) return
        pet.setImageResource(R.drawable.pet_dream)
        handler.postDelayed({
            if (poseRes == current) pet.setImageResource(current)
        }, 650)
    }

    private fun react() {
        showBubble(PetMessages.tap(this), 6000)
        pose(R.drawable.pet_wave)
        pet.animate().rotation(-4f).setDuration(90).withEndAction {
            pet.animate().rotation(4f).setDuration(120).withEndAction {
                pet.animate().rotation(0f).setDuration(120).start()
            }.start()
        }.start()
        handler.postDelayed({ pose(restPose()) }, 2200)
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