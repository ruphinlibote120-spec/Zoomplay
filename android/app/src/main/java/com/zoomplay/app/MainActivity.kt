package com.zoomplay.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import android.widget.CheckBox
import android.content.ContentValues
import android.media.MediaScannerConnection
import java.io.File
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private val ui = Handler(Looper.getMainLooper())

    private var all: List<Track> = emptyList()
    private var shown: List<Track> = emptyList()
    private var tab = 0
    private var group: Entry? = null

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private lateinit var chips: LinearLayout
    private lateinit var btnPlay: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnExtra: MaterialButton
    private lateinit var count: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var mini: View
    private lateinit var miniCover: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: ImageButton
    private lateinit var miniProgress: ProgressBar

    private lateinit var trackAdapter: TrackAdapter
    private lateinit var entryAdapter: EntryAdapter
    private val chipNames = listOf("Chansons", "Playlists", "Favoris", "Artistes", "Albums")

    private var audioGranted = false

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        audioGranted = hasAudioPermission()
        if (audioGranted) scan() else refresh()
    }

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startVoice() else {
            store.micOn = false
            updateMicUi()
            Toast.makeText(this, "Le micro est nécessaire pour les commandes vocales", Toast.LENGTH_LONG).show()
        }
    }

    private var pendingDelete: List<Track> = emptyList()

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT >= 30) {
                toast("Doublons supprimés")
                scan()
            } else {
                deleteTracks(pendingDelete)
            }
        }
    }

    private var pendingRename: List<Fix> = emptyList()

    private val writeLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == RESULT_OK) runRename(pendingRename) else toast("Autorisation refusée : rien n'a été renommé")
    }

    private val rescan = Runnable { scan() }

    private val observer = object : ContentObserver(ui) {
        override fun onChange(selfChange: Boolean) {
            ui.removeCallbacks(rescan)
            ui.postDelayed(rescan, 1500)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null && c.duration > 0) {
                miniProgress.progress = (c.currentPosition * 1000 / c.duration).toInt().coerceIn(0, 1000)
            }
            ui.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateMini()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        store = Store(this)
        setTheme(Themes.current(store).styleRes)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        search = findViewById(R.id.search)
        chips = findViewById(R.id.chips)
        btnPlay = findViewById(R.id.btnPlay)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnExtra = findViewById(R.id.btnExtra)
        count = findViewById(R.id.count)
        btnMic = findViewById(R.id.btnMic)
        mini = findViewById(R.id.mini)
        miniCover = findViewById(R.id.miniCover)
        miniTitle = findViewById(R.id.miniTitle)
        miniArtist = findViewById(R.id.miniArtist)
        miniPlay = findViewById(R.id.miniPlay)
        miniProgress = findViewById(R.id.miniProgress)
        roundCorners(miniCover, dp(8f))

        trackAdapter = TrackAdapter(
            store,
            onClick = { pos -> play(shown, pos, null) },
            onMore = { t, v -> trackMenu(t, v) }
        )
        entryAdapter = EntryAdapter(
            onClick = { e -> group = e; refresh() },
            onLong = { e -> e.pl?.let { playlistMenu(it) } }
        )
        list.layoutManager = LinearLayoutManager(this)

        chipNames.forEachIndexed { i, name ->
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 15f
            tv.setPadding(dp(18f).toInt(), dp(10f).toInt(), dp(18f).toInt(), dp(10f).toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(8f).toInt()
            tv.layoutParams = lp
            tv.setOnClickListener { tab = i; group = null; refresh() }
            chips.addView(tv)
        }

        findViewById<View>(R.id.btnSearch).setOnClickListener { toggleSearch() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { settingsDialog() }
        findViewById<View>(R.id.btnSort).setOnClickListener { sortDialog() }
        findViewById<View>(R.id.btnDup).setOnClickListener { findDuplicates() }
        findViewById<View>(R.id.btnFix).setOnClickListener { findBadTitles() }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh() }
        })

        btnPlay.setOnClickListener { if (shown.isNotEmpty()) play(shown, 0, null) }
        btnShuffle.setOnClickListener {
            if (shown.isNotEmpty()) play(shown, shown.indices.random(), true)
        }
        btnExtra.setOnClickListener {
            if (group != null) { group = null; refresh() } else askNewPlaylist(null)
        }
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            Toast.makeText(this, "Bibliothèque actualisée", Toast.LENGTH_SHORT).show()
            if (hasAudioPermission()) scan() else requestPerms()
        }
        btnMic.setOnClickListener { toggleMic() }
        empty.setOnClickListener { if (!hasAudioPermission()) requestPerms() }

        miniPlay.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.mediaItemCount == 0) {
                if (all.isNotEmpty()) play(all, 0, null)
            } else if (c.isPlaying) c.pause() else c.play()
        }
        findViewById<View>(R.id.miniNext).setOnClickListener { controller?.seekToNext() }
        mini.setOnClickListener { startActivity(Intent(this, PlayerActivity::class.java)) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (group != null) { group = null; refresh() }
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })

        audioGranted = hasAudioPermission()
        if (audioGranted) scan() else requestPerms()
        refresh()
        updateMicUi()
        if (savedInstanceState == null) handleViewIntent(intent)
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
                updateMini()
                pendingOpen?.let { pendingOpen = null; openUri(it) }
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(this))
        try {
            contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (e: Exception) {
        }
        ui.post(ticker)
    }

    override fun onResume() {
        super.onResume()
        if (hasAudioPermission()) {
            audioGranted = true
            scan()
        }
        if (store.micOn && hasMicPermission()) startVoice() else updateMicUi()
    }

    override fun onStop() {
        ui.removeCallbacks(ticker)
        ui.removeCallbacks(rescan)
        try { contentResolver.unregisterContentObserver(observer) } catch (e: Exception) {}
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        super.onStop()
    }

    // ---- Permissions ----

    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        val req = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            req.add(Manifest.permission.READ_MEDIA_AUDIO)
            req.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            req.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= 28) req.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permLauncher.launch(req.toTypedArray())
    }

    // ---- Library ----

    private fun scan() {
        if (!hasAudioPermission()) return
        Thread {
            val r = try { Library.scan(this) } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                all = r
                refresh()
                updateMini()
            }
        }.start()
    }

    private fun byIds(ids: List<Long>): List<Track> {
        val m = all.associateBy { it.id }
        return ids.mapNotNull { m[it] }
    }

    private fun sorted(l: List<Track>): List<Track> = when (store.sortMode) {
        1 -> l.sortedBy { it.title.lowercase() }
        2 -> l.sortedBy { it.artist.lowercase() }
        3 -> l.sortedByDescending { it.durationMs }
        else -> l.sortedByDescending { it.dateAdded }
    }

    private fun entries(): List<Entry> = when (tab) {
        1 -> store.playlists().map { Entry(it.name, it.ids.size, it, byIds(it.ids)) }
        3 -> all.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { Entry(it.key, it.value.size, null, it.value) }
        else -> all.groupBy { it.album.ifBlank { "Album inconnu" } }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { Entry(it.key, it.value.size, null, it.value) }
    }

    private fun refresh() {
        val q = search.text.toString().trim().lowercase()
        var g = group
        val gp = g?.pl
        if (gp != null) {
            val pid = gp.id
            g = store.playlists().firstOrNull { it.id == pid }?.let { Entry(it.name, it.ids.size, it, byIds(it.ids)) }
            group = g
        }
        val showEntries = g == null && (tab == 1 || tab == 3 || tab == 4)

        chips.forEachIndexedChild { i, tv ->
            val on = i == tab
            tv.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
            tv.setTextColor(tc(if (on) R.attr.zBg else R.attr.zText))
            tv.text = if (i == 0 && all.isNotEmpty()) "Chansons (" + all.size + ")" else chipNames[i]
        }

        btnPlay.visibility = if (showEntries) View.GONE else View.VISIBLE
        btnShuffle.visibility = if (showEntries) View.GONE else View.VISIBLE
        btnExtra.visibility = if (g != null || tab == 1) View.VISIBLE else View.GONE
        btnExtra.text = if (g != null) "← Retour" else "Nouvelle playlist"

        if (showEntries) {
            var es = entries()
            if (q.isNotEmpty()) es = es.filter { it.name.lowercase().contains(q) }
            entryAdapter.submit(es)
            if (list.layoutManager !is LinearLayoutManager || list.layoutManager is GridLayoutManager) list.layoutManager = LinearLayoutManager(this)
            if (list.adapter !== entryAdapter) list.adapter = entryAdapter
            shown = emptyList()
            count.text = es.size.toString() + " " + chipNames[tab].lowercase().trimEnd('s') + "(s)"
            showEmpty(
                es.isEmpty(),
                if (tab == 1) "Aucune playlist.\nAppuie sur « Nouvelle playlist » pour en créer une."
                else if (!hasAudioPermission()) "Autorise l'accès à la musique pour afficher tes titres.\nAppuie ici pour l'autoriser."
                else "Aucun résultat."
            )
        } else {
            var l: List<Track> = when {
                g != null -> if (g.pl != null) g.tracks else sorted(g.tracks)
                tab == 2 -> { val f = store.favorites(); sorted(all.filter { f.contains(it.id) }) }
                else -> sorted(all)
            }
            if (q.isNotEmpty()) {
                l = l.filter {
                    it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
                }
            }
            shown = l
            val wantGrid = store.listStyle == 3
            val isGrid = list.layoutManager is GridLayoutManager
            if (wantGrid != isGrid) list.layoutManager = if (wantGrid) GridLayoutManager(this, 2) else LinearLayoutManager(this)
            trackAdapter.submit(l)
            if (list.adapter !== trackAdapter) list.adapter = trackAdapter
            count.text = if (g != null) g.name + " · " + l.size else l.size.toString() + " titre(s)"
            showEmpty(
                l.isEmpty(),
                when {
                    !hasAudioPermission() -> "Autorise l'accès à la musique pour afficher tes titres.\nAppuie ici pour l'autoriser."
                    tab == 2 && g == null -> "Aucun favori pour l'instant.\nAppuie sur ⋮ à côté d'un titre pour l'ajouter."
                    q.isNotEmpty() -> "Aucun résultat."
                    g != null -> "Cette liste est vide."
                    else -> "Aucun fichier audio trouvé sur le téléphone."
                }
            )
        }
        updateMini()
    }

    private inline fun LinearLayout.forEachIndexedChild(f: (Int, TextView) -> Unit) {
        for (i in 0 until childCount) f(i, getChildAt(i) as TextView)
    }

    private fun toggleSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (search.visibility == View.GONE) {
            search.visibility = View.VISIBLE
            search.requestFocus()
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        } else {
            search.setText("")
            search.visibility = View.GONE
            imm.hideSoftInputFromWindow(search.windowToken, 0)
        }
    }

    private fun sortDialog() {
        val labels = arrayOf("Récemment ajoutés", "Titre (A-Z)", "Artiste (A-Z)", "Durée (la plus longue)")
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Trier les titres")
            .setSingleChoiceItems(labels, store.sortMode) { d, i ->
                store.sortMode = i
                d.dismiss()
                refresh()
            }
            .show()
    }

    private fun showEmpty(on: Boolean, msg: String) {
        empty.text = msg
        empty.visibility = if (on) View.VISIBLE else View.GONE
    }

    // ---- Lecture ----

    private fun play(tracks: List<Track>, index: Int, shuffle: Boolean?) {
        val c = controller ?: run {
            Toast.makeText(this, "Le lecteur démarre, réessaie dans un instant", Toast.LENGTH_SHORT).show()
            return
        }
        if (tracks.isEmpty()) return
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.size - 1), 0L)
        c.prepare()
        c.play()
    }

    private fun currentTrack(c: MediaController): Track? {
        val item: MediaItem = c.currentMediaItem ?: return null
        val id = item.mediaId.toLongOrNull() ?: -1L
        return all.firstOrNull { it.id == id } ?: Track(
            id, item.mediaMetadata.title?.toString() ?: "Titre",
            item.mediaMetadata.artist?.toString() ?: "", "", 0L, 0L, 0L
        )
    }

    private fun updateMini() {
        val c = controller
        val t = if (c != null) currentTrack(c) else null
        if (t == null || c == null) {
            miniTitle.text = "Zoomplay"
            miniArtist.text = if (all.isEmpty()) "Aucune musique en cours" else "Appuie sur ▶ pour lancer ta musique"
            miniCover.setImageResource(R.drawable.logo)
            miniPlay.setImageResource(R.drawable.ic_play)
            miniProgress.progress = 0
            trackAdapter.setPlaying(-1L)
            return
        }
        miniTitle.text = t.title
        miniArtist.text = t.artist
        if (miniCover.tag != t.id) ArtLoader.load(this, t, miniCover, 128)
        miniPlay.setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        trackAdapter.setPlaying(t.id)
    }

    // ---- Menus ----

    private fun trackMenu(t: Track, anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "Lire ensuite")
        m.menu.add(0, 2, 1, "Ajouter à la file")
        m.menu.add(0, 3, 2, if (store.isFav(t.id)) "Retirer des favoris" else "Ajouter aux favoris")
        m.menu.add(0, 4, 3, "Ajouter à une playlist")
        if (group?.pl != null) m.menu.add(0, 5, 4, "Retirer de la playlist")
        m.menu.add(0, 6, 5, "Renommer")
        m.setOnMenuItemClickListener { mi ->
            val c = controller
            when (mi.itemId) {
                1 -> if (c != null && c.mediaItemCount > 0) {
                    c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
                    toast("Lu ensuite")
                } else play(listOf(t), 0, null)
                2 -> if (c != null && c.mediaItemCount > 0) {
                    c.addMediaItem(t.toMediaItem())
                    toast("Ajouté à la file")
                } else play(listOf(t), 0, null)
                3 -> { store.toggleFav(t.id); refresh() }
                4 -> pickPlaylist(t)
                5 -> group?.pl?.let { p ->
                    val pls = store.playlists()
                    val real = pls.firstOrNull { it.id == p.id }
                    real?.ids?.remove(t.id)
                    store.savePlaylists(pls)
                    group = real?.let { Entry(it.name, it.ids.size, it, byIds(it.ids)) }
                    refresh()
                }
                6 -> renameDialog(t)
            }
            true
        }
        m.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun pickPlaylist(t: Track) {
        val pls = store.playlists()
        val names = pls.map { it.name }.toMutableList()
        names.add("+ Nouvelle playlist…")
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Ajouter à une playlist")
            .setItems(names.toTypedArray()) { _, i ->
                if (i == pls.size) askNewPlaylist(t)
                else {
                    val p = pls[i]
                    if (!p.ids.contains(t.id)) p.ids.add(t.id)
                    store.savePlaylists(pls)
                    toast("Ajouté à " + p.name)
                    refresh()
                }
            }
            .show()
    }

    private fun askNewPlaylist(t: Track?) {
        val et = EditText(this)
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
                    val pls = store.playlists()
                    val p = Store.Playlist(System.currentTimeMillis().toString(), name, ArrayList())
                    if (t != null) p.ids.add(t.id)
                    pls.add(p)
                    store.savePlaylists(pls)
                    refresh()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun playlistMenu(p: Store.Playlist) {
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle(p.name)
            .setItems(arrayOf("Lire", "Supprimer")) { _, i ->
                if (i == 0) {
                    val l = byIds(p.ids)
                    if (l.isNotEmpty()) play(l, 0, null)
                } else {
                    val pls = store.playlists().filter { it.id != p.id }
                    store.savePlaylists(pls)
                    refresh()
                }
            }
            .show()
    }

    private fun renameDialog(t: Track) {
        if (hasAudioPermission().not()) return
        val et = EditText(this)
        et.setText(t.title)
        et.setSelectAllOnFocus(true)
        et.setTextColor(tc(R.attr.zText))
        val pad = dp(20f).toInt()
        val box = android.widget.FrameLayout(this)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Renommer le fichier")
            .setView(box)
            .setPositiveButton("Renommer") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty() && n != t.title) applyFixes(listOf(Fix(t, n)))
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ---- Paramètres ----

    private fun settingsDialog() {
        val pad = dp(8f).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad * 2, pad, pad * 2, pad)
        var dlg: androidx.appcompat.app.AlertDialog? = null

        fun header(txt: String) {
            val h = TextView(this)
            h.text = txt
            h.textSize = 13f
            h.setTextColor(tc(R.attr.zAccent))
            h.setPadding(0, pad * 2, 0, pad / 2)
            box.addView(h)
        }
        fun radios(names: List<String>, cur: Int, onPick: (Int) -> Unit) {
            val rg = android.widget.RadioGroup(this)
            names.forEachIndexed { i, n ->
                val rb = android.widget.RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = n
                rb.setTextColor(tc(R.attr.zText))
                rb.isChecked = i == cur
                rg.addView(rb)
                rb.setOnClickListener { onPick(i) }
            }
            box.addView(rg)
        }
        fun switch(txt: String, cur: Boolean, onSet: (Boolean) -> Unit) {
            val sw = androidx.appcompat.widget.SwitchCompat(this)
            sw.text = txt
            sw.setTextColor(tc(R.attr.zText))
            sw.isChecked = cur
            sw.setPadding(0, pad, 0, pad)
            sw.setOnCheckedChangeListener { _, v -> onSet(v) }
            box.addView(sw)
        }
        fun action(txt: String, f: () -> Unit) {
            val tv = TextView(this)
            tv.text = txt
            tv.textSize = 16f
            tv.setTextColor(tc(R.attr.zText))
            tv.setPadding(0, pad * 2, 0, pad * 2)
            tv.setOnClickListener { dlg?.dismiss(); f() }
            box.addView(tv)
        }

        header("STYLE DE LA LISTE")
        radios(
            listOf("Classique", "Compact (sans pochette)", "Détaillé (artiste, album, date)", "Grille (2 colonnes)", "Cartes"),
            store.listStyle
        ) { store.listStyle = it; refresh() }

        header("TAILLE DU TEXTE")
        radios(listOf("Petit", "Normal", "Grand"), store.textSize) { store.textSize = it; refresh() }

        header("AFFICHAGE")
        switch("Afficher la pochette", store.showCover) { store.showCover = it; refresh() }
        switch("Afficher la durée", store.showDuration) { store.showDuration = it; refresh() }
        switch("Afficher l'artiste", store.showArtist) { store.showArtist = it; refresh() }
        switch("Titre complet sur deux lignes", store.twoLines) { store.twoLines = it; refresh() }

        header("AUTRES")
        action("🎨  Thème de l'application") { themeDialog() }
        action("↕  Trier les titres") { sortDialog() }

        val sv = ScrollView(this)
        sv.addView(box)
        dlg = MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Paramètres")
            .setView(sv)
            .setNegativeButton("Fermer", null)
            .show()
    }

    // ---- Ouvrir avec ----

    private var pendingOpen: Uri? = null

    private fun handleViewIntent(i: Intent?) {
        if (i?.action != Intent.ACTION_VIEW) return
        val u = i.data ?: return
        setIntent(Intent(this, MainActivity::class.java))
        if (controller == null) pendingOpen = u else openUri(u)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    private fun openUri(u: Uri) {
        val c = controller ?: run { pendingOpen = u; return }
        if (u.authority == "media") {
            val id = u.lastPathSegment?.toLongOrNull()
            val idx = all.indexOfFirst { it.id == id }
            if (idx >= 0) {
                play(all, idx, null)
                startActivity(Intent(this, PlayerActivity::class.java))
                return
            }
        }
        var name = "Titre"
        try {
            contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
        } catch (e: Exception) {
        }
        if (name == "Titre") name = u.lastPathSegment ?: name
        val title = name.substringBeforeLast('.', name)
        val item = MediaItem.Builder()
            .setMediaId(u.toString())
            .setUri(u)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(u).build())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Artiste inconnu")
                    .build()
            )
            .build()
        c.setMediaItems(listOf(item), 0, 0L)
        c.prepare()
        c.play()
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    // ---- Thèmes ----

    private fun themeDialog() {
        val cur = store.theme
        val pad = dp(8f).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad * 2, pad, pad * 2, pad)
        var dlg: androidx.appcompat.app.AlertDialog? = null
        Themes.all.forEachIndexed { i, t ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(0, pad, 0, pad)
            val sw = View(this)
            val bgc = android.graphics.drawable.GradientDrawable()
            bgc.shape = android.graphics.drawable.GradientDrawable.OVAL
            bgc.setColor(t.bg)
            bgc.setStroke(dp(2f).toInt(), t.accent)
            val inner = android.graphics.drawable.GradientDrawable()
            inner.shape = android.graphics.drawable.GradientDrawable.OVAL
            inner.setColor(t.accent)
            val ld = android.graphics.drawable.LayerDrawable(arrayOf(bgc, inner))
            ld.setLayerInset(1, dp(11f).toInt(), dp(11f).toInt(), dp(11f).toInt(), dp(11f).toInt())
            sw.background = ld
            row.addView(sw, LinearLayout.LayoutParams(dp(36f).toInt(), dp(36f).toInt()))
            val tv = TextView(this)
            tv.text = t.name + (if (t.dark) "" else "  (clair)") + (if (i == cur) "  ✓" else "")
            tv.setTextColor(tc(if (i == cur) R.attr.zAccent else R.attr.zText))
            tv.textSize = 16f
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(14f).toInt()
            row.addView(tv, lp)
            row.setOnClickListener {
                store.theme = i
                dlg?.dismiss()
                recreate()
            }
            box.addView(row)
        }
        val sv = ScrollView(this)
        sv.addView(box)
        dlg = MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Thème (" + Themes.all.size + " styles)")
            .setView(sv)
            .setNegativeButton("Fermer", null)
            .show()
    }

    // ---- Titres mal rédigés ----

    private fun findBadTitles() {
        if (!hasAudioPermission()) { requestPerms(); return }
        toast("Analyse des titres…")
        Thread {
            val fixes = all.filter { TitleFix.needsFix(it.title) }.map { Fix(it, TitleFix.clean(it.title)) }
            runOnUiThread { if (!isDestroyed) showFixes(fixes) }
        }.start()
    }

    private fun showFixes(fixes: List<Fix>) {
        if (fixes.isEmpty()) {
            MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
                .setTitle("Tout est correct")
                .setMessage("Aucun titre ne commence par une minuscule ou un tiret, ne finit par (0), (1)… ni ne contient de caractères inutiles.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val adapter = FixAdapter(fixes)
        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        val pad = dp(16f).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, 0, pad, 0)
        box.addView(rv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380f).toInt()))
        val dlg = MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle(fixes.size.toString() + " titre(s) à corriger")
            .setMessage("Tout est coché : appuie sur « Tout corriger » pour renommer d'un coup, ou décoche et modifie les noms à la main.")
            .setView(box)
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Tout décocher", null)
            .setPositiveButton("Tout corriger", null)
            .show()
        var allOn = true
        dlg.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            allOn = !allOn
            adapter.setAll(allOn)
            (it as TextView).text = if (allOn) "Tout décocher" else "Tout cocher"
        }
        dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val sel = fixes.filter { it.checked && TitleFix.sanitize(it.newTitle).isNotEmpty() }
            if (sel.isEmpty()) {
                toast("Aucun titre coché")
            } else {
                dlg.dismiss()
                applyFixes(sel)
            }
        }
    }

    private fun applyFixes(list: List<Fix>) {
        pendingRename = list
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val pi = MediaStore.createWriteRequest(contentResolver, list.map { it.t.uri })
                writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) {
                toast("Renommage impossible")
            }
        } else {
            runRename(list)
        }
    }

    private fun runRename(list: List<Fix>) {
        Thread {
            var ok = 0
            var failed = 0
            var recoverable: IntentSender? = null
            val rest = ArrayList<Fix>()
            for (f in list) {
                if (recoverable != null) { rest.add(f); continue }
                try {
                    if (renameOne(f)) ok++ else failed++
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                        recoverable = e.userAction.actionIntent.intentSender
                        rest.add(f)
                    } else failed++
                } catch (e: Exception) {
                    failed++
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val r = recoverable
                if (r != null) {
                    pendingRename = rest
                    writeLauncher.launch(IntentSenderRequest.Builder(r).build())
                } else {
                    toast(ok.toString() + " fichier(s) renommé(s)" + (if (failed > 0) ", $failed échec(s)" else ""))
                    scan()
                }
            }
        }.start()
    }

    private fun renameOne(f: Fix): Boolean {
        val name = TitleFix.sanitize(f.newTitle)
        if (name.isEmpty()) return false
        val ext = f.t.displayName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val newFile = name + ext
        if (newFile == f.t.displayName && name == f.t.title) return true
        val cv = ContentValues()
        cv.put(MediaStore.Audio.Media.DISPLAY_NAME, newFile)
        cv.put(MediaStore.Audio.Media.TITLE, name)
        if (Build.VERSION.SDK_INT >= 29) {
            return contentResolver.update(f.t.uri, cv, null, null) > 0
        }
        val old = File(f.t.path)
        if (!old.exists()) return false
        val dest = File(old.parentFile, newFile)
        if (dest.exists() && dest.absolutePath != old.absolutePath) return false
        if (dest.absolutePath != old.absolutePath && !old.renameTo(dest)) return false
        @Suppress("DEPRECATION")
        cv.put(MediaStore.Audio.Media.DATA, dest.absolutePath)
        val n = contentResolver.update(f.t.uri, cv, null, null)
        MediaScannerConnection.scanFile(this, arrayOf(dest.absolutePath), null, null)
        return n > 0
    }

    // ---- Doublons ----

    private fun findDuplicates() {
        if (!hasAudioPermission()) { requestPerms(); return }
        toast("Recherche des doublons…")
        Thread {
            val groups = Duplicates.find(all)
            runOnUiThread { if (!isDestroyed) showDuplicates(groups) }
        }.start()
    }

    private fun showDuplicates(groups: List<List<Track>>) {
        if (groups.isEmpty()) {
            MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
                .setTitle("Aucun doublon")
                .setMessage("Aucun titre en double n'a été trouvé dans ta bibliothèque.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val pad = dp(16f).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad / 2, pad, 0)
        val boxes = ArrayList<Pair<CheckBox, Track>>()
        groups.forEachIndexed { gi, g ->
            val h = TextView(this)
            h.text = "Doublon " + (gi + 1) + " · " + g.size + " fichiers"
            h.setTextColor(tc(R.attr.zAccent))
            h.textSize = 13f
            h.setPadding(0, pad / 2, 0, 0)
            box.addView(h)
            g.forEachIndexed { i, t ->
                val cb = CheckBox(this)
                cb.text = t.title + "\n" + fmtTime(t.durationMs) + " · ajouté le " + fmt.format(Date(t.dateAdded * 1000)) +
                    (if (i == 0) " · à garder" else "")
                cb.setTextColor(tc(R.attr.zText))
                cb.textSize = 14f
                cb.isChecked = i > 0
                box.addView(cb)
                boxes.add(Pair(cb, t))
            }
        }
        val sv = ScrollView(this)
        sv.addView(box)
        val wrap = LinearLayout(this)
        wrap.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380f).toInt()))
        val total = groups.sumOf { it.size - 1 }
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle(groups.size.toString() + " doublon(s) trouvé(s)")
            .setMessage("Tout est coché sauf le plus ancien de chaque groupe. Appuie sur « Tout supprimer » pour effacer les $total doublons, ou décoche ceux à garder.")
            .setView(wrap)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Tout supprimer") { _, _ ->
                val sel = boxes.filter { it.first.isChecked }.map { it.second }
                if (sel.isEmpty()) {
                    toast("Aucun fichier coché")
                } else {
                    MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
                        .setTitle("Supprimer " + sel.size + " fichier(s) ?")
                        .setMessage("Les fichiers seront effacés définitivement du téléphone.")
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Supprimer") { _, _ -> deleteTracks(sel) }
                        .show()
                }
            }
            .show()
    }

    private fun deleteTracks(ts: List<Track>) {
        if (ts.isEmpty()) return
        pendingDelete = ts
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(contentResolver, ts.map { it.uri })
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                return
            }
        } catch (e: Exception) {
            toast("Suppression impossible")
            return
        }
        Thread {
            var recoverable: IntentSender? = null
            var done = 0
            val rest = ArrayList<Track>()
            for (t in ts) {
                if (recoverable != null) { rest.add(t); continue }
                try {
                    done += contentResolver.delete(t.uri, null, null)
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                        recoverable = e.userAction.actionIntent.intentSender
                        rest.add(t)
                    }
                } catch (e: Exception) {
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val r = recoverable
                if (r != null) {
                    pendingDelete = rest
                    deleteLauncher.launch(IntentSenderRequest.Builder(r).build())
                } else {
                    toast("$done fichier(s) supprimé(s)")
                    scan()
                }
            }
        }.start()
    }

    // ---- Micro permanent ----

    private fun toggleMic() {
        if (store.micOn) {
            store.micOn = false
            startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_STOP))
            updateMicUi()
            toast("Micro désactivé")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
                .setTitle("Reconnaissance vocale absente")
                .setMessage("Ton téléphone n'a pas de service de reconnaissance vocale. Installe ou active « Google » (application Google) puis réessaie.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        if (!hasMicPermission()) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoice()
        batteryHint()
    }

    private fun startVoice() {
        store.micOn = true
        try {
            ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
        } catch (e: Exception) {
            store.micOn = false
            toast("Impossible de démarrer le micro")
        }
        updateMicUi()
    }

    private fun updateMicUi() {
        val on = store.micOn
        btnMic.setColorFilter(tc(if (on) R.attr.zAccent else R.attr.zMuted))
    }

    private fun batteryHint() {
        val sp = getSharedPreferences("zoomplay_ui", MODE_PRIVATE)
        if (sp.getBoolean("batteryHintShown", false)) return
        sp.edit().putBoolean("batteryHintShown", true).apply()
        MaterialAlertDialogBuilder(this, R.style.ZoomDialog)
            .setTitle("Micro activé")
            .setMessage(
                "Zoomplay t'écoute maintenant : dis « Zoom Play music », « Zoom Next », « Zoom Prev » ou « Zoom Pause ».\n\n" +
                    "Pour que l'écoute ne soit pas coupée écran éteint, autorise Zoomplay à fonctionner sans restriction de batterie."
            )
            .setPositiveButton("Régler la batterie") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton("Plus tard", null)
            .show()
    }
}
