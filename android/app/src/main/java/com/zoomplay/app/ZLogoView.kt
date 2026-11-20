package com.zoomplay.app

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Logo Zoomplay animé : les trois segments du Z s'allument tour à tour sur chaque coup de basse
 * de la chanson en cours, et les ondes autour vibrent avec le niveau de grave.
 */
class ZLogoView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private val seg = FloatArray(3)
    private var ringE = 0f
    private var sparkle = 0f
    private var ema = 0.15f
    private var prev = 0f
    private var lastHit = 0L
    private var next = 0
    private var lastFrame = 0L
    private var running = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrame = SystemClock.uptimeMillis()
        postOnAnimation(loop)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(loop)
        super.onDetachedFromWindow()
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = min(0.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        val fresh = now - VizBus.stamp < 250
        val b = if (fresh) VizBus.bass else 0f
        if (fresh) {
            val onset = b > 0.30f && b > ema * 1.18f + 0.04f && b > prev && now - lastHit > 130
            if (onset) {
                lastHit = now
                val strength = (0.65f + b * 0.6f).coerceAtMost(1f)
                seg[next] = strength
                if (next == 1) sparkle = 1f
                next = (next + 1) % 3
            }
            ema = ema * 0.93f + b * 0.07f
            prev = b
        }
        val k = exp(-dt * 7f)
        for (i in 0 until 3) seg[i] *= k
        sparkle *= exp(-dt * 6f)
        ringE = max(ringE * exp(-dt * 5f), b * 0.9f)
    }

    private fun lg(x0: Float, y0: Float, x1: Float, y1: Float, c: IntArray, pos: FloatArray) =
        LinearGradient(x0, y0, x1, y1, c, pos, Shader.TileMode.CLAMP)

    override fun onDraw(cv: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val s = size / 300f
        cv.save()
        cv.translate((width - size) / 2f, (height - size) / 2f)
        cv.scale(s, s)

        val w = SystemClock.uptimeMillis() / 1000f
        p.reset(); p.isAntiAlias = true
        rect.set(0f, 0f, 300f, 300f)

        // halo + fond
        glow.reset(); glow.isAntiAlias = true
        glow.color = 0x8C1A1A6E.toInt()
        glow.maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
        cv.drawRoundRect(rect, 76f, 76f, glow)
        p.shader = lg(0f, 0f, 300f, 300f, intArrayOf(0xFF3B1F9A.toInt(), 0xFF5B6CFF.toInt(), 0xFF2DD4FF.toInt(), 0xFFE44CFF.toInt()), floatArrayOf(0f, .35f, .65f, 1f))
        cv.drawRoundRect(rect, 76f, 76f, p)
        p.shader = lg(0f, 300f, 300f, 0f, intArrayOf(0xF22A1B8F.toInt(), 0x002A1B8F, 0x002A1B8F), floatArrayOf(0f, .55f, 1f))
        cv.drawRoundRect(rect, 76f, 76f, p)
        p.shader = lg(300f, 300f, 0f, 0f, intArrayOf(0xD9FF4FE0.toInt(), 0x00FF4FE0, 0x00FF4FE0), floatArrayOf(0f, .5f, 1f))
        cv.drawRoundRect(rect, 76f, 76f, p)
        p.shader = lg(0f, 0f, 300f, 300f, intArrayOf(0xFFD8B4FE.toInt(), 0xFF7DD3FC.toInt(), 0xFFF0ABFC.toInt()), floatArrayOf(0f, .5f, 1f))
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.alpha = 217
        rect.set(4f, 4f, 296f, 296f)
        cv.drawRoundRect(rect, 72f, 72f, p)

        val cx = 150f
        val cy = 150f

        // disque central
        p.reset(); p.isAntiAlias = true
        val coreR = 84f + ringE * 4f
        p.shader = RadialGradient(cx, cy, coreR, intArrayOf(0xD91E2FBF.toInt(), 0x592A2AD8), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, coreR, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0xFF67E8F9.toInt(); p.alpha = 230
        cv.drawCircle(cx, cy, coreR, p)

        // ondes qui vibrent avec la basse
        val base = floatArrayOf(99f, 111f, 123f)
        for (j in 0 until 3) {
            val wob = sin(w * 26f + j * 1.7f) * 2.2f * ringE + sin(w * 41f + j * 2.3f) * 1.2f * ringE
            val r = base[j] + ringE * (6f + j * 5f) + wob
            val a = (0.4f + 0.6f * ringE - j * 0.05f).coerceIn(0.2f, 1f)
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
            p.strokeWidth = 1.8f + 2.6f * ringE
            p.shader = when (j) {
                0 -> lg(cx + r, cy - r, cx - r, cy + r, intArrayOf(0xFFE879F9.toInt(), 0xFF60A5FA.toInt()), floatArrayOf(0f, 1f))
                1 -> lg(cx - r, cy - r, cx + r, cy + r, intArrayOf(0xFF67E8F9.toInt(), 0xFFC084FC.toInt()), floatArrayOf(0f, 1f))
                else -> null
            }
            if (j == 2) p.color = 0xFFF5D0FE.toInt()
            p.alpha = (a * 255).toInt()
            p.maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
            cv.drawCircle(cx, cy, r, p)
        }

        // segments du Z
        drawBar(cv, 68f, 76f, seg[0], intArrayOf(0xFF22A6FF.toInt(), 0xFF2563EB.toInt(), 0xFF3B3BFF.toInt()))
        drawBar(cv, 68f, 190f, seg[2], intArrayOf(0xFF4338FF.toInt(), 0xFFA21CFF.toInt(), 0xFFFF4FD8.toInt()))
        drawDiagonal(cv, seg[1])

        // étoile
        cv.save()
        val sc = 1f + 0.9f * sparkle
        cv.translate(252f, 114f)
        cv.rotate(sparkle * 40f)
        cv.scale(sc, sc)
        path.reset()
        path.moveTo(0f, -16f); path.lineTo(4f, -4f); path.lineTo(16f, 0f); path.lineTo(4f, 4f)
        path.lineTo(0f, 16f); path.lineTo(-4f, 4f); path.lineTo(-16f, 0f); path.lineTo(-4f, -4f); path.close()
        glow.reset(); glow.isAntiAlias = true; glow.color = 0xFFFFFFFF.toInt()
        glow.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        cv.drawPath(path, glow)
        p.reset(); p.isAntiAlias = true; p.color = 0xFFFFFFFF.toInt()
        cv.drawPath(path, p)
        cv.restore()

        cv.restore()
    }

    private fun drawBar(cv: Canvas, x: Float, y: Float, e: Float, cols: IntArray) {
        cv.save()
        val sc = 1f + 0.2f * e
        cv.scale(sc, sc, x + 82f, y + 17f)
        rect.set(x, y, x + 164f, y + 34f)
        glow.reset(); glow.isAntiAlias = true
        glow.color = cols[1]; glow.alpha = (110 + 140 * e).toInt().coerceAtMost(255)
        glow.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        cv.drawRoundRect(rect, 13f, 13f, glow)
        p.reset(); p.isAntiAlias = true
        p.shader = lg(x, y, x + 164f, y + 34f, cols, floatArrayOf(0f, .6f, 1f))
        cv.drawRoundRect(rect, 13f, 13f, p)
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.color = 0xFFFFFFFF.toInt(); p.alpha = 204
        rect.set(x + .8f, y + .8f, x + 163.2f, y + 33.2f)
        cv.drawRoundRect(rect, 12.4f, 12.4f, p)
        p.reset(); p.isAntiAlias = true
        p.shader = lg(0f, y + 3f, 0f, y + 12f, intArrayOf(0xBFFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f))
        rect.set(x + 8f, y + 3f, x + 156f, y + 12f)
        cv.drawRoundRect(rect, 4.5f, 4.5f, p)
        if (e > 0.01f) {
            p.reset(); p.isAntiAlias = true; p.color = 0xFFFFFFFF.toInt(); p.alpha = (0.5f * e * 255).toInt()
            rect.set(x, y, x + 164f, y + 34f)
            cv.drawRoundRect(rect, 13f, 13f, p)
        }
        cv.restore()
    }

    private fun drawDiagonal(cv: Canvas, e: Float) {
        cv.save()
        val sc = 1f + 0.2f * e
        cv.scale(sc, sc, 155f, 160f)
        path.reset()
        path.moveTo(216f, 100f); path.lineTo(246f, 116f); path.lineTo(94f, 220f)
        path.lineTo(64f, 212f); path.lineTo(64f, 194f); path.close()
        glow.reset(); glow.isAntiAlias = true
        glow.color = 0xFFA855F7.toInt(); glow.alpha = (110 + 140 * e).toInt().coerceAtMost(255)
        glow.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        cv.drawPath(path, glow)
        p.reset(); p.isAntiAlias = true
        p.shader = lg(246f, 100f, 64f, 220f,
            intArrayOf(0xFF22D3EE.toInt(), 0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFFF43FB5.toInt()),
            floatArrayOf(0f, .35f, .7f, 1f))
        cv.drawPath(path, p)
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFFFFFFFF.toInt(); p.alpha = 217
        cv.drawPath(path, p)
        if (e > 0.01f) {
            p.reset(); p.isAntiAlias = true; p.color = 0xFFFFFFFF.toInt(); p.alpha = (0.5f * e * 255).toInt()
            cv.drawPath(path, p)
        }
        cv.restore()
    }
}
