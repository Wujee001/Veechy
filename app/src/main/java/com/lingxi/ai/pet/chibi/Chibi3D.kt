package com.lingxi.ai.pet.chibi

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 程序化 Q 版 Veechy：真三维。
 *
 * 不依赖任何 3D 引擎 / 模型文件：骨骼树 + 椭球体部件，
 * 透视投影到屏幕后按协方差拟合椭圆，再用径向渐变做体积光照。
 * 因此转身、躺下、走路、跳跃都是真实的三维变换，不是图片轮播。
 */

// ---------------------------------------------------------------- 向量 / 旋转

data class V3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f)

class R3 {
    val v = FloatArray(9)

    init { setIdentity() }

    fun setIdentity() {
        v.fill(0f)
        v[0] = 1f; v[4] = 1f; v[8] = 1f
    }

    fun set(o: R3) {
        System.arraycopy(o.v, 0, v, 0, 9)
    }

    /** this × o */
    fun mul(o: R3): R3 {
        val r = R3()
        for (i in 0..2) {
            for (j in 0..2) {
                var s = 0f
                for (k in 0..2) s += v[i * 3 + k] * o.v[k * 3 + j]
                r.v[i * 3 + j] = s
            }
        }
        return r
    }

    fun xform(p: V3): V3 = V3(
        v[0] * p.x + v[1] * p.y + v[2] * p.z,
        v[3] * p.x + v[4] * p.y + v[5] * p.z,
        v[6] * p.x + v[7] * p.y + v[8] * p.z
    )

    companion object {
        fun rx(a: Float): R3 {
            val r = R3()
            val c = cos(a); val s = sin(a)
            r.v[4] = c; r.v[5] = -s
            r.v[7] = s; r.v[8] = c
            return r
        }

        fun ry(a: Float): R3 {
            val r = R3()
            val c = cos(a); val s = sin(a)
            r.v[0] = c; r.v[2] = s
            r.v[6] = -s; r.v[8] = c
            return r
        }

        fun rz(a: Float): R3 {
            val r = R3()
            val c = cos(a); val s = sin(a)
            r.v[0] = c; r.v[1] = -s
            r.v[3] = s; r.v[4] = c
            return r
        }

        fun euler(x: Float, y: Float, z: Float): R3 = ry(y).mul(rx(x)).mul(rz(z))
    }
}

// ---------------------------------------------------------------- 骨骼与部件

class Bone(val parent: Bone?, val offset: V3) {
    val localPos = V3()
    val localRot = V3()
    val worldR = R3()
    val worldP = V3()
}

class Part(
    val bone: Bone,
    val cx: Float, val cy: Float, val cz: Float,
    val rx: Float, val ry: Float, val rz: Float,
    val color: Int,
    val eye: Boolean = false,
    val gloss: Float = 0.55f
)

class Rig {

    val root = Bone(null, V3(0f, 0f, 0f))
    val chest = Bone(root, V3(0f, 0.13f, 0f))
    val head = Bone(chest, V3(0f, 0.17f, 0f))

    val armL = Bone(chest, V3(-0.115f, 0.055f, 0f))
    val armR = Bone(chest, V3(0.115f, 0.055f, 0f))
    val foreL = Bone(armL, V3(0f, -0.11f, 0f))
    val foreR = Bone(armR, V3(0f, -0.11f, 0f))

    val legL = Bone(root, V3(-0.07f, -0.02f, 0f))
    val legR = Bone(root, V3(0.07f, -0.02f, 0f))
    val shinL = Bone(legL, V3(0f, -0.12f, 0f))
    val shinR = Bone(legR, V3(0f, -0.12f, 0f))

    private val bones = listOf(root, chest, head, armL, armR, foreL, foreR, legL, legR, shinL, shinR)

    val parts = ArrayList<Part>()

    init {
        buildBody()
    }

    private fun add(
        bone: Bone,
        cx: Float, cy: Float, cz: Float,
        rx: Float, ry: Float, rz: Float,
        color: Int,
        eye: Boolean = false,
        gloss: Float = 0.55f
    ) {
        parts.add(Part(bone, cx, cy, cz, rx, ry, rz, color, eye, gloss))
    }

    private fun buildBody() {
        val skin = Color.rgb(255, 227, 214)
        val hair = Color.rgb(59, 44, 92)
        val hairLight = Color.rgb(86, 66, 128)
        val clothDark = Color.rgb(43, 33, 56)
        val coat = Color.rgb(244, 240, 252)
        val violet = Color.rgb(124, 92, 255)

        // 腿 / 鞋
        for (side in 0..1) {
            val thigh = if (side == 0) legL else legR
            val shin = if (side == 0) shinL else shinR
            add(thigh, 0f, -0.055f, 0f, 0.047f, 0.075f, 0.05f, clothDark)
            add(shin, 0f, -0.05f, 0f, 0.038f, 0.062f, 0.042f, skin)
            add(shin, 0f, -0.118f, 0.032f, 0.048f, 0.032f, 0.075f, clothDark)
        }

        // 胯 / 短裙
        add(root, 0f, -0.035f, 0f, 0.112f, 0.07f, 0.088f, Color.rgb(112, 92, 168))

        // 白外套 + 黑上衣
        add(chest, 0f, -0.02f, -0.018f, 0.128f, 0.135f, 0.092f, coat)
        add(chest, 0f, -0.022f, 0.036f, 0.104f, 0.118f, 0.072f, clothDark)
        // 胸口的小 V
        add(chest, 0f, 0.005f, 0.104f, 0.022f, 0.026f, 0.008f, Color.rgb(150, 220, 255), gloss = 0.8f)

        // 手臂：白袖 + 手
        for (side in 0..1) {
            val upper = if (side == 0) armL else armR
            val fore = if (side == 0) foreL else foreR
            add(upper, 0f, -0.05f, 0f, 0.048f, 0.072f, 0.05f, coat)
            add(fore, 0f, -0.045f, 0f, 0.036f, 0.055f, 0.04f, coat)
            add(fore, 0f, -0.098f, 0f, 0.04f, 0.038f, 0.04f, skin)
        }

        // 头：后发、脸、刘海、侧发、呆毛
        add(head, 0f, 0.015f, -0.045f, 0.205f, 0.2f, 0.2f, hair)
        add(head, 0f, -0.16f, -0.135f, 0.165f, 0.24f, 0.085f, hair)
        add(head, 0f, 0f, 0.012f, 0.183f, 0.172f, 0.168f, skin)
        add(head, 0f, 0.115f, 0.075f, 0.18f, 0.085f, 0.12f, hair)
        add(head, -0.085f, 0.055f, 0.128f, 0.075f, 0.075f, 0.055f, hair)
        add(head, 0.075f, 0.06f, 0.13f, 0.07f, 0.07f, 0.05f, hair)
        add(head, -0.175f, -0.075f, 0.03f, 0.052f, 0.17f, 0.06f, hairLight)
        add(head, 0.175f, -0.075f, 0.03f, 0.052f, 0.17f, 0.06f, hairLight)
        add(head, 0.02f, 0.215f, -0.01f, 0.016f, 0.055f, 0.016f, hair)

        // V 形发饰
        add(head, -0.15f, 0.075f, 0.1f, 0.03f, 0.02f, 0.012f, Color.rgb(140, 230, 255), gloss = 0.9f)
        add(head, -0.125f, 0.09f, 0.1f, 0.016f, 0.03f, 0.01f, violet, gloss = 0.9f)

        // 眼睛：眼白 + 虹膜 + 高光
        for (side in 0..1) {
            val sx = if (side == 0) -0.072f else 0.072f
            add(head, sx, -0.012f, 0.152f, 0.036f, 0.044f, 0.018f, Color.WHITE, eye = true)
            add(head, sx, -0.014f, 0.163f, 0.026f, 0.034f, 0.012f, violet, eye = true, gloss = 0.8f)
            add(head, sx + 0.009f, 0.0f, 0.172f, 0.009f, 0.011f, 0.006f, Color.WHITE, eye = true, gloss = 0.9f)
        }

        // 腮红 / 嘴
        add(head, -0.105f, -0.048f, 0.13f, 0.026f, 0.014f, 0.012f, Color.rgb(255, 176, 186), gloss = 0.2f)
        add(head, 0.105f, -0.048f, 0.13f, 0.026f, 0.014f, 0.012f, Color.rgb(255, 176, 186), gloss = 0.2f)
        add(head, 0f, -0.078f, 0.155f, 0.016f, 0.008f, 0.008f, Color.rgb(196, 110, 120), gloss = 0.3f)
    }

    fun update() {
        for (b in bones) {
            val localR = R3.euler(b.localRot.x, b.localRot.y, b.localRot.z)
            val off = V3(
                b.offset.x + b.localPos.x,
                b.offset.y + b.localPos.y,
                b.offset.z + b.localPos.z
            )
            val parent = b.parent
            if (parent == null) {
                b.worldR.set(localR)
                b.worldP.x = off.x; b.worldP.y = off.y; b.worldP.z = off.z
            } else {
                b.worldR.set(parent.worldR.mul(localR))
                val p = parent.worldR.xform(off)
                b.worldP.x = parent.worldP.x + p.x
                b.worldP.y = parent.worldP.y + p.y
                b.worldP.z = parent.worldP.z + p.z
            }
        }
    }
}

// ---------------------------------------------------------------- 渲染

class ChibiRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val camPos = V3(0f, 0.46f, 2.6f)
    private val fwd: V3
    private val right: V3
    private val up: V3

    // 视空间光源：左上前方
    private val lx = -0.48f
    private val ly = 0.72f
    private val lz = 0.5f

    init {
        val target = V3(0f, 0.4f, 0f)
        fwd = norm(V3(target.x - camPos.x, target.y - camPos.y, target.z - camPos.z))
        right = norm(cross(fwd, V3(0f, 1f, 0f)))
        up = cross(right, fwd)
    }

    private fun norm(v: V3): V3 {
        val l = sqrt(v.x * v.x + v.y * v.y + v.z * v.z).coerceAtLeast(1e-6f)
        return V3(v.x / l, v.y / l, v.z / l)
    }

    private fun cross(a: V3, b: V3): V3 = V3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

    private fun dot(a: V3, b: V3): Float = a.x * b.x + a.y * b.y + a.z * b.z

    private fun toView(p: V3): V3 {
        val d = V3(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z)
        return V3(dot(d, right), dot(d, up), dot(d, fwd))
    }

    private fun dirToView(p: V3): V3 = V3(dot(p, right), dot(p, up), dot(p, fwd))

    private class Item {
        var z = 0f
        var sx = 0f
        var sy = 0f
        var major = 0f
        var minor = 0f
        var angle = 0f
        var color = 0
        var gloss = 0.5f
    }

    private val items = ArrayList<Item>()

    fun draw(
        canvas: Canvas,
        rig: Rig,
        figurePx: Float,
        blink: Float,
        shadowScale: Float,
        dim: Float
    ) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val focal = figurePx * camPos.z / 0.86f
        val cx = w * 0.5f
        val cy = h * 0.56f

        // 地面阴影
        val groundWorld = rig.root.worldP
        val ground = toView(V3(groundWorld.x, 0f, groundWorld.z))
        if (ground.z > 0.2f) {
            val gs = focal / ground.z
            val gx = cx + ground.x * gs
            val gy = cy - ground.y * gs
            val rw = 0.30f * gs * shadowScale
            val rh = 0.085f * gs * shadowScale
            paint.shader = RadialGradient(
                gx, gy, rw.coerceAtLeast(1f),
                intArrayOf(0x40534A86, 0x00534A86),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.translate(gx, gy)
            canvas.scale(1f, (rh / rw).coerceIn(0.1f, 1f))
            canvas.drawCircle(0f, 0f, rw.coerceAtLeast(1f), paint)
            canvas.restore()
            paint.shader = null
        }

        items.clear()
        for (part in rig.parts) {
            val b = part.bone
            val wc = b.worldR.xform(V3(part.cx, part.cy, part.cz))
            val world = V3(
                b.worldP.x + wc.x,
                b.worldP.y + wc.y,
                b.worldP.z + wc.z
            )
            val vc = toView(world)
            if (vc.z < 0.25f) continue
            val s = focal / vc.z

            val blinkY = if (part.eye) blink else 1f
            val axes = arrayOf(
                b.worldR.xform(V3(part.rx, 0f, 0f)),
                b.worldR.xform(V3(0f, part.ry * blinkY, 0f)),
                b.worldR.xform(V3(0f, 0f, part.rz))
            )

            var a = 0f; var bb = 0f; var c = 0f
            for (axis in axes) {
                val va = dirToView(axis)
                val px = va.x * s
                val py = -va.y * s
                a += px * px
                bb += px * py
                c += py * py
            }

            val half = (a + c) * 0.5f
            val diff = sqrt(((a - c) * 0.5f) * ((a - c) * 0.5f) + bb * bb)
            val l1 = (half + diff).coerceAtLeast(0.0001f)
            val l2 = (half - diff).coerceAtLeast(0.0001f)

            val it = Item()
            it.z = vc.z
            it.sx = cx + vc.x * s
            it.sy = cy - vc.y * s
            it.major = sqrt(l1).coerceAtLeast(0.8f)
            it.minor = sqrt(l2).coerceAtLeast(0.8f)
            it.angle = (atan2(2 * bb, a - c) * 0.5f * 180f / Math.PI).toFloat()
            it.color = part.color
            it.gloss = part.gloss
            items.add(it)
        }

        // 画家算法：先远后近
        items.sortByDescending { it.z }

        for (it in items) {
            val base = it.color
            val light = mix(base, Color.WHITE, 0.35f + it.gloss * 0.35f, dim)
            val mid = mix(base, Color.BLACK, 0f, dim)
            val dark = mix(base, Color.BLACK, 0.34f, dim)

            canvas.save()
            canvas.translate(it.sx, it.sy)
            canvas.rotate(it.angle)
            canvas.scale(it.major, it.minor)
            paint.shader = RadialGradient(
                lx * 0.42f, -ly * 0.42f, 1.35f,
                intArrayOf(light, mid, dark),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
            paint.shader = null
        }
    }

    private fun mix(color: Int, other: Int, amount: Float, dim: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        val r = (Color.red(color) + (Color.red(other) - Color.red(color)) * a) * dim
        val g = (Color.green(color) + (Color.green(other) - Color.green(color)) * a) * dim
        val b = (Color.blue(color) + (Color.blue(other) - Color.blue(color)) * a) * dim
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }
}