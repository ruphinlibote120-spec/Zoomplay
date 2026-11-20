package com.zoomplay.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews

/** Widget d'écran d'accueil : logo, titre en cours et boutons précédent / lecture / suivant. */
class ZoomWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    companion object {
        private fun key(ctx: Context, code: Int, req: Int): PendingIntent {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(ctx, PlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
            return PendingIntent.getService(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, ZoomWidget::class.java))
                if (ids.isEmpty()) return
                val p = ctx.applicationContext.getSharedPreferences("zoomplay", Context.MODE_PRIVATE)
                val title = p.getString("wTitle", "") ?: ""
                val artist = p.getString("wArtist", "") ?: ""
                val playing = p.getBoolean("wPlaying", false)
                val v = RemoteViews(ctx.packageName, R.layout.widget_zoom)
                v.setTextViewText(R.id.wTitle, if (title.isEmpty()) "Zoomplay" else title)
                v.setTextViewText(R.id.wArtist, if (title.isEmpty()) "Appuie pour ouvrir" else artist)
                v.setImageViewResource(R.id.wPlay, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                v.setOnClickPendingIntent(R.id.wPrev, key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 11))
                v.setOnClickPendingIntent(R.id.wPlay, key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 12))
                v.setOnClickPendingIntent(R.id.wNext, key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT, 13))
                val open = PendingIntent.getActivity(
                    ctx, 10,
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                v.setOnClickPendingIntent(R.id.wCover, open)
                v.setOnClickPendingIntent(R.id.wInfo, open)
                mgr.updateAppWidget(ids, v)
            } catch (e: Exception) {
            }
        }
    }
}
