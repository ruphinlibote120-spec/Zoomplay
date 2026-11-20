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
    private var countedId = -1L

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
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { queueSoon() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                countedId = -1L
                savePos()
                updateWidget()
            }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) { updateWidget() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { savePos() }
            override fun onRepeatModeChanged(repeatMode: Int) { savePos() }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) { savePos() }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                savePos()
                updateWidget()
                handler.removeCallbacks(posTick)
                if (isPlaying) handler.postDelayed(posTick, 5000)
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
        restore()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private val posTick = object : Runnable {
        override fun run() {
            savePos()
            countPlay()
            handler.postDelayed(this, 5000)
        }
    }

    /** Compte une écoute quand le titre a joué assez longtemps (20 s, ou 60 % pour les très courts). */
    private fun countPlay() {
        if (!player.isPlaying) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (id == countedId) return
        val dur = player.duration
        val need = if (dur > 0) minOf(20_000L, dur * 6 / 10) else 20_000L
        if (player.currentPosition >= need) {
            countedId = id
            store.bumpPlay(id)
        }
    }

    private fun updateWidget() {
        val md = player.mediaMetadata
        store.prefs.edit()
            .putString("wTitle", md.title?.toString() ?: "")
            .putString("wArtist", md.artist?.toString() ?: "")
            .putBoolean("wPlaying", player.isPlaying)
            .apply()
        ZoomWidget.updateAll(this)
    }
    private val queueSave = Runnable { saveQueue() }

    private fun queueSoon() {
        handler.removeCallbacks(queueSave)
        handler.postDelayed(queueSave, 1000)
    }

    private fun saveQueue() {
        val n = player.mediaItemCount
        if (n == 0) return
        val ids = (0 until n).joinToString(",") { player.getMediaItemAt(it).mediaId }
        store.prefs.edit().putString("queue", ids).apply()
        savePos()
    }

    private fun savePos() {
        val e = store.prefs.edit()
            .putBoolean("qShuffle", player.shuffleModeEnabled)
            .putInt("qRepeat", player.repeatMode)
            .putFloat("qSpeed", player.playbackParameters.speed)
        if (player.mediaItemCount > 0) {
            e.putInt("qIndex", player.currentMediaItemIndex).putLong("qPos", player.currentPosition)
        }
        e.apply()
    }

    /** Remet la dernière file de lecture, en pause, à l'endroit où l'utilisateur s'était arrêté. */
    private fun restore() {
        if (store.prefs.contains("qShuffle")) {
            player.shuffleModeEnabled = store.prefs.getBoolean("qShuffle", false)
            player.repeatMode = store.prefs.getInt("qRepeat", Player.REPEAT_MODE_OFF)
            player.setPlaybackSpeed(store.prefs.getFloat("qSpeed", 1f))
        }
        val saved = store.prefs.getString("queue", "") ?: ""
        if (saved.isEmpty()) return
        val ids = saved.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return
        val idx = store.prefs.getInt("qIndex", 0)
        val pos = store.prefs.getLong("qPos", 0L)
        val shuffle = store.prefs.getBoolean("qShuffle", false)
        val repeat = store.prefs.getInt("qRepeat", Player.REPEAT_MODE_OFF)
        val speed = store.prefs.getFloat("qSpeed", 1f)
        Thread {
            val lib = try { Library.scan(this).associateBy { it.id } } catch (e: Exception) { emptyMap() }
            val items = ids.mapNotNull { lib[it] }.map { it.toMediaItem() }
            handler.post {
                if (items.isEmpty() || player.mediaItemCount > 0) return@post
                val curId = ids.getOrNull(idx)?.toString()
                val start = items.indexOfFirst { it.mediaId == curId }.coerceAtLeast(0)
                player.shuffleModeEnabled = shuffle
                player.repeatMode = repeat
                player.setPlaybackSpeed(speed)
                player.setMediaItems(items, start, pos)
                player.prepare()
            }
        }.start()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        store.prefs.edit().putBoolean("wPlaying", false).apply()
        ZoomWidget.updateAll(this)
        saveQueue()
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
        VizBus.sessionId = sessionId
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

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val f = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val saved = store.prefs.getString("queue", "") ?: ""
            val ids = saved.split(",").mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) {
                f.setException(UnsupportedOperationException("Aucune file à reprendre"))
                return f
            }
            val idx = store.prefs.getInt("qIndex", 0)
            val pos = store.prefs.getLong("qPos", 0L)
            Thread {
                try {
                    val lib = Library.scan(this@PlaybackService).associateBy { it.id }
                    val items = ids.mapNotNull { lib[it] }.map { it.toMediaItem() }
                    if (items.isEmpty()) {
                        f.setException(UnsupportedOperationException("File introuvable"))
                    } else {
                        val curId = ids.getOrNull(idx)?.toString()
                        val start = items.indexOfFirst { it.mediaId == curId }.coerceAtLeast(0)
                        f.set(MediaSession.MediaItemsWithStartPosition(items, start, pos))
                    }
                } catch (e: Exception) {
                    f.setException(e)
                }
            }.start()
            return f
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
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        // pochette choisie par l'utilisateur pour ce titre, sinon le logo
        if (uri.scheme == "file") {
            try {
                val b = BitmapFactory.decodeFile(uri.path)
                if (b != null) return Futures.immediateFuture(Bitmap.createScaledBitmap(b, 384, 384, true))
            } catch (e: Exception) {
            }
        }
        return Futures.immediateFuture(bmp)
    }
}
