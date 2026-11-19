package com.zoomplay.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import java.util.concurrent.Executors

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

fun roundCorners(v: View, radiusPx: Float) {
    v.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    v.clipToOutline = true
}

/** Pochette générée : dégradé, sillons de vinyle et initiale du titre. */
class CoverDrawable(private val title: String, seed: Long) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(26, 255, 255, 255)
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 255, 255, 255)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val h1 = ((seed * 2654435761L) and 0x7fffffffL).toInt() % 360

    override fun draw(c: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val a = h1.toFloat()
        val bb = (h1 + 50) % 360
        p.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            Color.HSVToColor(floatArrayOf(a, 0.6f, 0.55f)),
            Color.HSVToColor(floatArrayOf(bb.toFloat(), 0.7f, 0.30f)),
            Shader.TileMode.CLAMP
        )
        c.drawRect(b, p)
        ring.strokeWidth = (w / 90f).coerceAtLeast(1f)
        val cx = b.left + w * 0.3f
        val cy = b.top + h * 0.65f
        var r = w * 0.1f
        while (r < w * 1.2f) {
            c.drawCircle(cx, cy, r, ring)
            r += w * 0.1f
        }
        txt.textSize = h * 0.42f
        val ch = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
        c.drawText(ch, b.left + w / 2, b.top + h / 2 - (txt.descent() + txt.ascent()) / 2, txt)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/** Charge la pochette de l'album en arrière-plan ; la pochette générée sert de repli. */
object ArtLoader {
    private val cache = LruCache<Long, Bitmap>(60)
    private val ex = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, t: Track, iv: ImageView, sizePx: Int) {
        iv.setImageDrawable(CoverDrawable(t.title, t.id))
        iv.tag = t.id
        val key = t.id xor (sizePx.toLong() shl 40)
        val hit = cache.get(key)
        if (hit != null) {
            iv.setImageBitmap(hit)
            return
        }
        val app = ctx.applicationContext
        ex.execute {
            val bmp = fetch(app, t, sizePx) ?: return@execute
            cache.put(key, bmp)
            main.post { if (iv.tag == t.id) iv.setImageBitmap(bmp) }
        }
    }

    private fun fetch(ctx: Context, t: Track, size: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(t.uri, Size(size, size), null)
        } else {
            ctx.contentResolver.openInputStream(t.artUri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) {
        null
    }
}
