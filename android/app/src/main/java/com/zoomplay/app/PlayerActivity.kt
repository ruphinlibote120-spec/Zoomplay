package com.zoomplay.app

import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    private val speeds = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    private var currentId = -1L

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
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        store = Store(this)
        val zt = Themes.current(store)
        setTheme(zt.styleRes)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        val top = tc(R.attr.zPlayerTop)
        val bottom = tc(R.attr.zBg)
        findViewById<View>(android.R.id.content).background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)
        )
        window.statusBarColor = top
        window.navigationBarColor = bottom
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !zt.dark

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

        roundCorners(cover, dp(16f))
        cover.setImageResource(R.drawable.cover_placeholder)
        cover.post {
            val lp = cover.layoutParams
            lp.height = cover.width
            cover.layoutParams = lp
        }
        title.isSelected = true

        findViewById<View>(R.id.btnDown).setOnClickListener { finish() }
        findViewById<View>(R.id.btnShare).setOnClickListener { share() }
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
        findViewById<View>(R.id.btnAddPl).setOnClickListener { pickPlaylist() }
        findViewById<View>(R.id.btnEq).setOnClickListener { EqDialog.show(this, controller) }
        findViewById<View>(R.id.btnQueue).setOnClickListener { queueDialog() }
        findViewById<View>(R.id.btnMore).setOnClickListener { moreMenu(it) }

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
        currentId = id
        title.text = item.mediaMetadata.title?.toString() ?: "Titre"
        artist.text = item.mediaMetadata.artist?.toString() ?: ""
        title.isSelected = true
        btnPlay.setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        val fav = store.isFav(id)
        btnLike.setImageResource(if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        btnLike.setColorFilter(tc(if (fav) R.attr.zAccent else R.attr.zText))
        btnShuffle.setColorFilter(tc(if (c.shuffleModeEnabled) R.attr.zAccent else R.attr.zMuted))
        btnRepeat.setImageResource(
            if (c.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        btnRepeat.setColorFilter(tc(if (c.repeatMode == Player.REPEAT_MODE_OFF) R.attr.zMuted else R.attr.zAccent))
    }

    private fun share() {
        if (currentId < 0) return
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currentId)
        val i = Intent(Intent.ACTION_SEND)
        i.type = "audio/*"
        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, "Partager ce titre"))
    }

    private fun moreMenu(anchor: View) {
        val c = controller
        val sp = c?.playbackParameters?.speed ?: 1f
        val left = store.sleepAt - System.currentTimeMillis()
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "Vitesse de lecture (" + (if (sp == sp.toInt().toFloat()) sp.toInt().toString() else sp.toString()) + "×)")
        m.menu.add(0, 2, 1, if (store.sleepAt > 0 && left > 0) "Minuteur de sommeil (" + fmtTime(left) + ")" else "Minuteur de sommeil")
        m.setOnMenuItemClickListener {
            if (it.itemId == 1) speedDialog() else sleepDialog()
            true
        }
        m.show()
    }

    private fun speedDialog() {
        val c = controller ?: return
        val labels = speeds.map { (if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()) + "×" }.toTypedArray()
        val cur = speeds.indexOfFirst { it == c.playbackParameters.speed }.let { if (it < 0) 1 else it }
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Vitesse de lecture")
            .setSingleChoiceItems(labels, cur) { d, i ->
                c.setPlaybackSpeed(speeds[i])
                d.dismiss()
            }
            .show()
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
                if (mins[i] > 0) Toast.makeText(this, "Pause dans " + mins[i] + " min", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun pickPlaylist() {
        if (currentId < 0) return
        val id = currentId
        val pls = store.playlists()
        val names = pls.map { it.name }.toMutableList()
        names.add("+ Nouvelle playlist…")
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Ajouter à une playlist")
            .setItems(names.toTypedArray()) { _, i ->
                if (i == pls.size) {
                    val et = android.widget.EditText(this)
                    et.hint = "Nom de la playlist"
                    et.setTextColor(tc(R.attr.zText))
                    et.setHintTextColor(tc(R.attr.zMuted))
                    val pad = dp(20f).toInt()
                    val box = android.widget.FrameLayout(this)
                    box.setPadding(pad, pad / 2, pad, 0)
                    box.addView(et)
                    MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
                        .setTitle("Nouvelle playlist")
                        .setView(box)
                        .setPositiveButton("Créer") { _, _ ->
                            val name = et.text.toString().trim()
                            if (name.isNotEmpty()) {
                                val all = store.playlists()
                                all.add(Store.Playlist(System.currentTimeMillis().toString(), name, arrayListOf(id)))
                                store.savePlaylists(all)
                                Toast.makeText(this, "Ajouté à $name", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                } else {
                    val p = pls[i]
                    if (!p.ids.contains(id)) p.ids.add(id)
                    store.savePlaylists(pls)
                    Toast.makeText(this, "Ajouté à " + p.name, Toast.LENGTH_SHORT).show()
                }
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
