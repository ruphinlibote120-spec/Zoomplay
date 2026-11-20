package com.zoomplay.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Identifiant de session audio du lecteur, partagé entre le service et l'écran de lecture. */
object VizBus {
    @Volatile var sessionId: Int = 0
    /** Niveau de grave (0..1) et heure de la dernière mesure : lus par le logo animé. */
    @Volatile var bass: Float = 0f
    @Volatile var voice: Float = 0f
    @Volatile var stamp: Long = 0L
}

/** Visualiseur de son en direct : barres de mixer (LED) ou barres lisses, avec crêtes. */
class VizView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val n = 26
    @Volatile private var target = FloatArray(n)
    private val lv = FloatArray(n)
    private val pk = FloatArray(n)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var viz: Visualizer? = null
    private var running = false
    private var noPerm = false
    private val store = Store(ctx)

    var onNeedPermission: (() -> Unit)? = null

    init {
        setOnClickListener {
            if (noPerm) onNeedPermission?.invoke()
            else store.vizStyle = if (store.vizStyle == 0) 1 else 0
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val t = target
            for (i in 0 until n) {
                lv[i] += (t[i] - lv[i]) * (if (t[i] > lv[i]) 0.55f else 0.16f)
                pk[i] = if (lv[i] > pk[i]) lv[i] else max(lv[i], pk[i] - 0.012f)
            }
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start() {
        stop()
        running = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            noPerm = true
        } else {
            noPerm = false
            try {
                val v = Visualizer(VizBus.sessionId)
                v.enabled = false
                v.captureSize = Visualizer.getCaptureSizeRange()[1]
                v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer?, wave: ByteArray?, rate: Int) {}
                    override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) {
                        if (fft != null) compute(fft, rate)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                v.enabled = true
                viz = v
            } catch (e: Exception) {
                viz = null
            }
        }
        postOnAnimation(loop)
    }

    fun stop() {
        running = false
        removeCallbacks(loop)
        try { viz?.enabled = false } catch (e: Exception) {}
        try { viz?.release() } catch (e: Exception) {}
        viz = null
        target = FloatArray(n)
    }

    private fun compute(fft: ByteArray, rate: Int) {
        val bins = fft.size / 2
        val out = FloatArray(n)
        var lo = 1
        for (b in 0 until n) {
            var hi = (bins.toDouble().pow((b + 1).toDouble() / n)).toInt().coerceIn(lo + 1, bins - 1)
            var m = 0f
            for (k in lo..hi) {
                val re = fft[2 * k].toInt()
                val im = fft[2 * k + 1].toInt()
                m = max(m, sqrt((re * re + im * im).toFloat()))
            }
            val db = 20f * log10(m + 1f)
            var l = ((db - 6f) / 36f).coerceIn(0f, 1f)
            l = min(1f, l * (0.75f + 0.5f * b / n))
            out[b] = l
            lo = hi
        }
        target = out
        // BASSE (< 130 Hz) et VOIX (300 Hz – 3,4 kHz) pour le logo animé
        val sr = if (rate > 8000000) rate / 1000.0 else 44100.0
        val binHz = sr / fft.size
        val kb = (130.0 / binHz).toInt().coerceIn(1, bins - 1)
        var bm = 0f
        for (k in 1..kb) {
            val re = fft[2 * k].toInt(); val im = fft[2 * k + 1].toInt()
            bm = max(bm, sqrt((re * re + im * im).toFloat()))
        }
        val v0 = Math.ceil(300.0 / binHz).toInt().coerceIn(1, bins - 2)
        val v1 = (3400.0 / binHz).toInt().coerceIn(v0 + 1, bins - 1)
        var sum = 0f
        for (k in v0..v1) {
            val re = fft[2 * k].toInt(); val im = fft[2 * k + 1].toInt()
            sum += (re * re + im * im).toFloat()
        }
        val vm = sqrt(sum / (v1 - v0 + 1))
        VizBus.bass = ((20f * log10(bm + 1f) - 6f) / 36f).coerceIn(0f, 1f)
        VizBus.voice = ((20f * log10(vm + 1f) - 6f) / 36f).coerceIn(0f, 1f)
        VizBus.stamp = android.os.SystemClock.uptimeMillis()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (noPerm) {
            paint.color = context.tc(R.attr.zMuted)
            paint.textSize = context.dp(13f)
            paint.textAlign = Paint.Align.CENTER
            c.drawText("Touche ici pour autoriser le micro et voir le son", w / 2, h / 2, paint)
            return
        }
        val gap = context.dp(3f)
        val bw = (w - gap * (n - 1)) / n
        val led = store.vizStyle == 0
        val off = context.tc(R.attr.zLine)
        val accent = context.tc(R.attr.zAccent)
        val cap = context.tc(R.attr.zText)
        for (i in 0 until n) {
            val x = i * (bw + gap)
            if (led) {
                val segs = 12
                val g = context.dp(2f)
                val sh = (h - g * (segs - 1)) / segs
                val on = Math.round(lv[i] * segs)
                val pkS = min(segs - 1, Math.round(pk[i] * segs))
                for (s in 0 until segs) {
                    val y = h - (s + 1) * sh - s * g
                    val col = if (s < 7) 0xFF3B9E3B.toInt() else if (s < 10) 0xFFEFA927.toInt() else 0xFFE24B4A.toInt()
                    paint.color = if (s < on || s == pkS) col else off
                    c.drawRect(x, y, x + bw, y + sh, paint)
                }
            } else {
                val bh = max(context.dp(2f), lv[i] * h)
                paint.color = accent
                c.drawRect(x, h - bh, x + bw, h, paint)
                paint.color = cap
                val py = h - pk[i] * h - context.dp(3f)
                c.drawRect(x, max(0f, py), x + bw, max(0f, py) + context.dp(2f), paint)
            }
        }
    }
}
