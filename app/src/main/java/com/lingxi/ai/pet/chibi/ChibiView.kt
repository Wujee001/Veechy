package com.lingxi.ai.pet.chibi

import android.content.Context
import android.graphics.Canvas
import android.util.TypedValue
import android.view.View
import java.util.Calendar
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * 1.2 cm 高的三维 Veechy。
 *
 * 行为链：发呆 → 走来走去 → 跳一下 → 坐下 → 躺下 → 挥手，
 * 不点也会自己动；点她会回头挥手或蹦一下；深夜更安静、更容易躺下闭眼。
 */
class ChibiView(context: Context) : View(context) {

    private enum class Act { IDLE, WALK, JUMP, WAVE, SIT, LIE }

    private val rig = Rig()
    private val renderer = ChibiRenderer()
    private val rnd = Random()

    /** 人物实际身高：12 mm。 */
    private val figurePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_MM, 12f, resources.displayMetrics
    )

    private var state = Act.IDLE
    private var stateStart = 0L
    private var stateDur = 3000L

    private var rootX = 0f
    private var startX = 0f
    private var targetX = 0f
    private var dir = 1

    private var blinkStart = 0L
    private var nextBlinkAt = 0L

    private var jumpHeight = 0f

    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 33)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stateStart = now()
        removeCallbacks(frame)
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = now()
        advance(t)
        applyPose(t)
        rig.update()
        val shadow = (1f - jumpHeight * 1.6f).coerceIn(0.55f, 1.05f)
        renderer.draw(canvas, rig, figurePx, blink(t), shadow, if (isNight()) 0.85f else 1f)
    }

    // ------------------------------------------------------------ 对外动作

    fun poke() {
        setState(if (rnd.nextBoolean()) Act.WAVE else Act.JUMP, now())
    }

    fun wave() {
        setState(Act.WAVE, now())
    }

    // ------------------------------------------------------------ 行为链

    private fun now(): Long = android.os.SystemClock.uptimeMillis()

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 23 || h <= 5
    }

    private fun advance(t: Long) {
        if (t - stateStart < stateDur) return
        if (state == Act.WALK) rootX = targetX
        chooseNext(t)
    }

    private fun chooseNext(t: Long) {
        val r = rnd.nextFloat()
        val night = isNight()
        val next = if (night) {
            when {
                r < 0.45f -> Act.LIE
                r < 0.65f -> Act.SIT
                r < 0.90f -> Act.IDLE
                else -> Act.WALK
            }
        } else {
            when {
                r < 0.34f -> Act.WALK
                r < 0.50f -> Act.IDLE
                r < 0.62f -> Act.JUMP
                r < 0.72f -> Act.WAVE
                r < 0.86f -> Act.SIT
                else -> Act.LIE
            }
        }
        setState(next, t)
    }

    private fun setState(act: Act, t: Long) {
        state = act
        stateStart = t
        when (act) {
            Act.IDLE -> stateDur = 2600 + rnd.nextInt(2600).toLong()

            Act.WALK -> {
                startX = rootX
                targetX = -0.55f + rnd.nextFloat() * 1.1f
                if (abs(targetX - rootX) < 0.18f) {
                    targetX = if (rootX > 0) rootX - 0.45f else rootX + 0.45f
                }
                targetX = targetX.coerceIn(-0.58f, 0.58f)
                dir = if (targetX >= rootX) 1 else -1
                stateDur = (abs(targetX - startX) / 0.30f * 1000).toLong().coerceAtLeast(800)
            }

            Act.JUMP -> stateDur = 1050
            Act.WAVE -> stateDur = 2000
            Act.SIT -> stateDur = 5000 + rnd.nextInt(4000).toLong()
            Act.LIE -> stateDur = 7000 + rnd.nextInt(6000).toLong()
        }
    }

    // ------------------------------------------------------------ 姿势

    private fun applyPose(t: Long) {
        val u = ((t - stateStart).toFloat() / stateDur.toFloat()).coerceIn(0f, 1f)
        val sec = t / 1000f
        val local = (t - stateStart) / 1000f

        var rootY = sin(sec * 2f) * 0.006f
        var yaw = 0.32f + sin(sec * 0.7f) * 0.12f
        var pitch = 0f
        var roll = 0f

        var legL = 0f
        var legR = 0f
        var shinL = 0f
        var shinR = 0f

        var armLX = 0f
        var armRX = 0f
        var armLZ = 0.14f
        var armRZ = -0.14f
        var foreLX = -0.25f
        var foreRX = -0.25f

        var headX = 0f
        var headY = 0f
        var headZ = 0f
        var chestX = 0f

        jumpHeight = 0f

        when (state) {
            Act.IDLE -> {
                headZ = sin(sec * 0.9f) * 0.05f
                headY = sin(sec * 0.5f) * 0.18f
                chestX = sin(sec * 2f) * 0.02f
            }

            Act.WALK -> {
                rootX = startX + (targetX - startX) * u
                val ph = local * 7.5f
                legL = sin(ph) * 0.55f
                legR = -sin(ph) * 0.55f
                shinL = max(0f, -sin(ph)) * 0.7f
                shinR = max(0f, sin(ph)) * 0.7f
                armLX = -sin(ph) * 0.42f
                armRX = sin(ph) * 0.42f
                yaw = dir * 1.35f
                rootY += abs(sin(ph)) * 0.022f
                roll = sin(ph) * 0.04f
                headY = -dir * 0.25f
            }

            Act.JUMP -> {
                val h = if (u in 0.15f..0.85f) {
                    (sin(Math.PI * ((u - 0.15f) / 0.7f)).toFloat() * 0.26f).coerceAtLeast(0f)
                } else 0f
                jumpHeight = h
                rootY += h
                val crouch = when {
                    u < 0.15f -> sin(Math.PI * (u / 0.15f)).toFloat() * 0.5f
                    u > 0.85f -> sin(Math.PI * ((1f - u) / 0.15f)).toFloat() * 0.5f
                    else -> 0f
                }
                val tuck = if (h > 0.01f) 1f else 0f
                legL = -0.5f * crouch - 0.55f * tuck
                legR = legL
                shinL = 0.9f * crouch + 0.9f * tuck
                shinR = shinL
                armLZ = 0.14f + 1.7f * tuck
                armRZ = -0.14f - 1.7f * tuck
                headX = -0.12f * tuck
            }

            Act.WAVE -> {
                armRZ = 2.35f + sin(local * 9f) * 0.28f
                foreRX = -0.35f
                headZ = 0.12f
                headY = 0.1f
                yaw = 0.12f
            }

            Act.SIT -> {
                rootY -= 0.17f
                legL = -1.35f
                legR = -1.35f
                shinL = 0.15f
                shinR = 0.15f
                armLX = -0.3f
                armRX = -0.3f
                headZ = sin(sec * 0.8f) * 0.07f
            }

            Act.LIE -> {
                pitch = 1.45f
                rootY = -0.14f + sin(sec * 1.4f) * 0.004f
                yaw = 0f
                armLZ = 0.4f
                armRZ = -0.4f
                headX = -0.12f
            }
        }

        rig.root.localPos.x = rootX
        rig.root.localPos.y = 0.30f + rootY
        rig.root.localPos.z = 0f
        rig.root.localRot.x = pitch
        rig.root.localRot.y = yaw
        rig.root.localRot.z = roll

        rig.chest.localRot.x = chestX
        rig.chest.localRot.y = 0f
        rig.chest.localRot.z = 0f

        rig.head.localRot.x = headX
        rig.head.localRot.y = headY
        rig.head.localRot.z = headZ

        rig.armL.localRot.x = armLX
        rig.armL.localRot.z = armLZ
        rig.armR.localRot.x = armRX
        rig.armR.localRot.z = armRZ
        rig.foreL.localRot.x = foreLX
        rig.foreR.localRot.x = foreRX

        rig.legL.localRot.x = legL
        rig.legR.localRot.x = legR
        rig.shinL.localRot.x = shinL
        rig.shinR.localRot.x = shinR
    }

    private fun blink(t: Long): Float {
        if (state == Act.LIE && t - stateStart > 1600) return 0.08f
        if (t > nextBlinkAt) {
            blinkStart = t
            nextBlinkAt = t + 2600 + rnd.nextInt(2800)
        }
        val d = t - blinkStart
        return when {
            d in 0L..140L -> 1f - (d / 140f) * 0.92f
            d in 140L..280L -> 0.08f + ((d - 140) / 140f) * 0.92f
            else -> 1f
        }
    }
}