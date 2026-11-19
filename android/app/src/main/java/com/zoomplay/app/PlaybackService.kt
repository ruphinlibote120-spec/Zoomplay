package com.zoomplay.app

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs
import kotlin.math.log10

/** Lecture en arrière-plan, notification de lecture, amplificateur et égaliseur. */
class PlaybackService : MediaSessionService() {
    companion object {
        const val CMD_FX = "com.zoomplay.app.FX"
        const val CMD_SLEEP = "com.zoomplay.app.SLEEP"
        val EQ_FREQS = intArrayOf(60, 230, 910, 3600, 14000)
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var store: Store
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachFx(audioSessionId)
            }
        })
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setSessionActivity(open)
            .setBitmapLoader(LogoLoader(this))
            .build()
        val provider = DefaultMediaNotificationProvider(this)
        provider.setSmallIcon(R.drawable.ic_notif)
        setMediaNotificationProvider(provider)
        attachFx(player.audioSessionId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseFx()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private fun releaseFx() {
        try { eq?.release() } catch (e: Exception) {}
        try { loud?.release() } catch (e: Exception) {}
        eq = null
        loud = null
    }

    private fun attachFx(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        releaseFx()
        try {
            eq = Equalizer(0, sessionId)
            loud = LoudnessEnhancer(sessionId)
        } catch (e: Exception) {
            eq = null
            loud = null
        }
        applyFx()
    }

    /** Applique le volume amplifié (100 à 300 %) et les 5 bandes de l'égaliseur. */
    private fun applyFx() {
        val boost = store.eqBoost.coerceIn(100, 300)
        val bands = store.eqBands
        try {
            loud?.let {
                it.setTargetGain((2000.0 * log10(boost / 100.0)).toInt())
                it.enabled = boost > 100
            }
        } catch (e: Exception) {}
        try {
            val e = eq ?: return
            val range = e.bandLevelRange
            val minL = range[0].toInt()
            val maxL = range[1].toInt()
            for (i in 0 until e.numberOfBands.toInt()) {
                val centerHz = e.getCenterFreq(i.toShort()) / 1000
                var best = 0
                for (k in EQ_FREQS.indices) {
                    if (abs(EQ_FREQS[k] - centerHz) < abs(EQ_FREQS[best] - centerHz)) best = k
                }
                val level = (bands[best] * 100).coerceIn(minL, maxL)
                e.setBandLevel(i.toShort(), level.toShort())
            }
            e.enabled = bands.any { it != 0 }
        } catch (ex: Exception) {}
    }

    private fun setSleep(min: Int) {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        sleepRunnable = null
        if (min > 0) {
            val r = Runnable {
                player.pause()
                store.sleepAt = 0L
            }
            sleepRunnable = r
            handler.postDelayed(r, min * 60_000L)
            store.sleepAt = System.currentTimeMillis() + min * 60_000L
        } else {
            store.sleepAt = 0L
        }
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_FX, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_FX -> applyFx()
                CMD_SLEEP -> setSleep(args.getInt("min", 0))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updated = mediaItems.map {
                it.buildUpon()
                    .setUri(it.requestMetadata.mediaUri ?: it.localConfiguration?.uri)
                    .build()
            }.toMutableList()
            return Futures.immediateFuture(updated)
        }
    }
}

/** Fournit l'icône Zoomplay comme image de la notification, quel que soit le titre. */
class LogoLoader(ctx: android.content.Context) : BitmapLoader {
    private val bmp: Bitmap = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)
        .let { Bitmap.createScaledBitmap(it, 384, 384, true) }

    override fun supportsMimeType(mimeType: String) = true
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = Futures.immediateFuture(bmp)
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = Futures.immediateFuture(bmp)
}
