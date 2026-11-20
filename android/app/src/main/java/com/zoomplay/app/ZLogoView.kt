package com.zoomplay.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Vrai logo Zoomplay, sans aucune retouche, animé avec la musique en cours :
 * les trois segments du Z s'allument tour à tour sur les coups de BASSE,
 * les ondes autour s'élargissent et vibrent avec la VOIX.
 */
class ZLogoView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val opts = BitmapFactory.Options().apply { inScaled = false }
    private fun bmp(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id, opts)

    private val base = bmp(R.drawable.logo)
    private val top = bmp(R.drawable.zl_top)
    private val dia = bmp(R.drawable.zl_dia)
    private val bot = bmp(R.drawable.zl_bot)
    private val ring = bmp(R.drawable.zl_ring)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val white = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
    }

    // segments : 0 haut, 1 diagonale, 2 bas
    private val seg = FloatArray(3)
    private val cxs = floatArrayOf(249.5f, 265f, 249.5f)
    private val cys = floatArrayOf(143.5f, 258f, 365.5f)
    private val grow = floatArrayOf(0.10f, 0.085f, 0.10f)
    private var next = 0

    private val waves = ArrayList<Long>()
    private var voiceLvl = 0f

    private var emaB = 0.15f
    private var prevB = 0f
    private var lastHit = 0L
    private var emaV = 0.1f
    private var prevV = 0f
    private var lastWave = 0L
    private var peakV = 0.25f
    private var lastFrame = 0L
    private var running = false

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
        val v = if (fresh) VizBus.voice else 0f

        if (fresh) {
            // BASSE seule -> segments du Z, un par coup, dans l'ordre
            val onset = b > 0.30f && b > emaB * 1.25f + 0.04f && b > prevB && now - lastHit > 140
            if (onset) {
                lastHit = now
                seg[next] = (0.7f + b * 0.5f).coerceAtMost(1f)
                next = (next + 1) % 3
            }
            emaB = emaB * 0.93f + b * 0.07f
            prevB = b

            // VOIX -> ondes
            peakV = max(max(v, 0.25f), peakV * exp(-dt * 0.4f))
            val vn = (v / peakV).coerceIn(0f, 1f)
            val onsetV = vn > 0.30f && vn > emaV * 1.3f && vn > prevV && now - lastWave > 120
            if (onsetV) {
                lastWave = now
                waves.add(now)
                if (waves.size > 8) waves.removeAt(0)
            }
            emaV = emaV * 0.9f + vn * 0.1f
            prevV = vn
            voiceLvl += (vn - voiceLvl) * (if (vn > voiceLvl) 0.6f else 0.15f)
        } else {
            voiceLvl *= exp(-dt * 8f)
        }

        val k = exp(-dt * 7f)
        for (i in 0 until 3) seg[i] *= k
        val it = waves.iterator()
        while (it.hasNext()) if (now - it.next() > 900) it.remove()
    }

    override fun onDraw(c: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val s = size / 512f
        val now = SystemClock.uptimeMillis()
        c.save()
        c.translate((width - size) / 2f, (height - size) / 2f)
        c.scale(s, s)

        c.drawBitmap(base, 0f, 0f, paint)

        // ondes qui partent du centre à chaque syllabe
        for (t0 in waves) {
            val age = (now - t0) / 1000f
            val sc = 1f + 0.10f * (1f - exp(-age * 6f))
            val a = (0.8f * exp(-age * 5f)).coerceIn(0f, 1f)
            drawScaled(c, ring, 256f, 256f, sc, paint, a)
        }
        // vibration des cercles pendant que la voix chante
        if (voiceLvl > 0.03f) {
            val a = (0.75f * voiceLvl).coerceIn(0f, 1f)
            drawScaled(c, ring, 256f, 256f, 1f + 0.05f * voiceLvl, paint, a)
            drawScaled(c, ring, 256f, 256f, 1f - 0.035f * voiceLvl, paint, a)
        }

        // segments du Z sur la basse
        val order = intArrayOf(2, 0, 1)
        val bms = arrayOf(top, dia, bot)
        for (i in order) {
            val e = seg[i]
            if (e > 0.01f) {
                val sc = 1f + grow[i] * e
                drawScaled(c, bms[i], cxs[i], cys[i], sc, paint, 1f)
                drawScaled(c, bms[i], cxs[i], cys[i], sc, white, 0.28f * e)
            }
        }
        c.restore()
    }

    private fun drawScaled(c: Canvas, bm: Bitmap, cx: Float, cy: Float, sc: Float, p: Paint, alpha: Float) {
        p.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        c.save()
        c.scale(sc, sc, cx, cy)
        c.drawBitmap(bm, 0f, 0f, p)
        c.restore()
        p.alpha = 255
    }
}
