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
    fun load(ctx: Context, t: Track, iv: ImageView, sizePx: Int) {
        iv.tag = t.id
        iv.setImageResource(R.drawable.logo)
    }
}
