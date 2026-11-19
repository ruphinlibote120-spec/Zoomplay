package com.zoomplay.app

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : AppCompatActivity() {

    private lateinit var store: Store
    private val ui = Handler(Looper.getMainLooper())
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var dragging = false

    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var seek: SeekBar
    private lateinit var tCur: TextView
    private lateinit var tDur: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnLike: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnSpeed: MaterialButton
    private lateinit var btnSleep: MaterialButton

    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    private var speedIdx = 0
    private var currentId = -1L
    private var currentTrack: Track? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { update() }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null && !dragging && c.duration > 0) {
                seek.progress = (c.currentPosition * 1000 / c.duration).toInt().coerceIn(0, 1000)
                tCur.text = fmtTime(c.currentPosition)
                tDur.text = fmtTime(c.duration)
            }
            updateSleepLabel()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        store = Store(this)

        cover = findViewById(R.id.cover)
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        seek = findViewById(R.id.seek)
        tCur = findViewById(R.id.tCur)
        tDur = findViewById(R.id.tDur)
        btnPlay = findViewById(R.id.btnPlay)
        btnLike = findViewById(R.id.btnLike)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnSleep = findViewById(R.id.btnSleep)

        roundCorners(cover, dp(20f))
        cover.post {
            val lp = cover.layoutParams
            lp.height = cover.width
            cover.layoutParams = lp
        }

        findViewById<View>(R.id.btnDown).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPrev).setOnClickListener { controller?.seekToPrevious() }
        findViewById<View>(R.id.btnNext).setOnClickListener { controller?.seekToNext() }
        btnPlay.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
        }
        btnShuffle.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.shuffleModeEnabled = !c.shuffleModeEnabled
        }
        btnRepeat.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        btnLike.setOnClickListener {
            if (currentId >= 0) { store.toggleFav(currentId); update() }
        }
        btnSpeed.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            speedIdx = (speedIdx + 1) % speeds.size
            c.setPlaybackSpeed(speeds[speedIdx])
            update()
        }
        btnSleep.setOnClickListener { sleepDialog() }
        findViewById<View>(R.id.btnEq).setOnClickListener { EqDialog.show(this, controller) }
        findViewById<View>(R.id.btnQueue).setOnClickListener { queueDialog() }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val c = controller ?: return
                if (fromUser && c.duration > 0) tCur.text = fmtTime(c.duration * p / 1000)
            }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                val c = controller
                if (c != null && c.duration > 0) c.seekTo(c.duration * (s?.progress ?: 0) / 1000)
                dragging = false
            }
        })
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                c.sendCustomCommand(SessionCommand(PlaybackService.CMD_FX, Bundle.EMPTY), Bundle.EMPTY)
                update()
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(this))
        ui.post(ticker)
    }

    override fun onStop() {
        ui.removeCallbacks(ticker)
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        super.onStop()
    }

    private fun update() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null) {
            title.text = "Aucune musique"
            artist.text = ""
            return
        }
        val id = item.mediaId.toLongOrNull() ?: -1L
        val t = Track(
            id, item.mediaMetadata.title?.toString() ?: "Titre",
            item.mediaMetadata.artist?.toString() ?: "",
            item.mediaMetadata.albumTitle?.toString() ?: "", 0L, 0L, 0L
        )
        // L'identifiant de pochette est retrouvé via MediaStore (loadThumbnail utilise l'URI du titre).
        title.text = t.title
        artist.text = t.artist
        if (id != currentId) {
            currentId = id
            currentTrack = t
            ArtLoader.load(this, t, cover, 640)
        }
        btnPlay.setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        btnLike.setImageResource(if (store.isFav(id)) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        btnLike.setColorFilter(getColor(if (store.isFav(id)) R.color.accent else R.color.muted))
        btnShuffle.setColorFilter(getColor(if (c.shuffleModeEnabled) R.color.accent else R.color.muted))
        btnRepeat.setImageResource(
            if (c.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        btnRepeat.setColorFilter(getColor(if (c.repeatMode == Player.REPEAT_MODE_OFF) R.color.muted else R.color.accent))
        val sp = c.playbackParameters.speed
        speedIdx = speeds.indexOfFirst { it == sp }.let { if (it < 0) 0 else it }
        btnSpeed.text = (if (sp == sp.toInt().toFloat()) sp.toInt().toString() else sp.toString()) + "×"
        updateSleepLabel()
    }

    private fun updateSleepLabel() {
        val left = store.sleepAt - System.currentTimeMillis()
        btnSleep.text = if (store.sleepAt > 0 && left > 0) "Minuteur " + fmtTime(left) else "Minuteur"
    }

    private fun sleepDialog() {
        val labels = arrayOf("Désactivé", "10 minutes", "20 minutes", "30 minutes", "45 minutes", "60 minutes")
        val mins = intArrayOf(0, 10, 20, 30, 45, 60)
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Minuteur de sommeil")
            .setItems(labels) { _, i ->
                val b = Bundle()
                b.putInt("min", mins[i])
                controller?.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY), b)
                ui.postDelayed({ updateSleepLabel() }, 300)
            }
            .show()
    }

    private fun queueDialog() {
        val c = controller ?: return
        val n = c.mediaItemCount
        if (n == 0) return
        val names = Array(n) { i ->
            val md = c.getMediaItemAt(i).mediaMetadata
            (if (i == c.currentMediaItemIndex) "▶ " else "") + (md.title ?: "Titre") +
                (md.artist?.let { " — $it" } ?: "")
        }
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("File d'attente ($n)")
            .setItems(names) { _, i ->
                c.seekToDefaultPosition(i)
                c.play()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }
}
