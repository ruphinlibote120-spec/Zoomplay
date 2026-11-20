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

/** Couleur du thème courant (attribut zBg, zText, zAccent…). */
fun Context.tc(attr: Int): Int {
    val tv = android.util.TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

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

/** Une seule image (logo Zoomplay) pour tous les titres. */
object ArtLoader {
    private val cache = LruCache<String, Bitmap>(48)

    fun load(ctx: Context, t: Track, iv: ImageView, sizePx: Int) {
        iv.tag = t.id
        if (t.cover.isNotEmpty()) {
            val key = t.cover + "@" + sizePx
            var b = cache.get(key)
            if (b == null) {
                try {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(t.cover, o)
                    var s = 1
                    while (o.outWidth / (s * 2) >= sizePx) s *= 2
                    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                    b = BitmapFactory.decodeFile(t.cover, o2)
                    if (b != null) cache.put(key, b)
                } catch (e: Exception) {
                    b = null
                }
            }
            if (b != null) { iv.setImageBitmap(b); return }
        }
        iv.setImageResource(R.drawable.logo)
    }
}
