package com.zoomplay.app

import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrackAdapter(
    private val store: Store,
    private val onClick: (Int) -> Unit,
    private val onMore: (Track, View) -> Unit
) : RecyclerView.Adapter<TrackAdapter.VH>() {

    private var items: List<Track> = emptyList()
    private var playingId: Long = -1L
    private val dateFmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView? = v.findViewById(R.id.cover)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val extra: TextView? = v.findViewById(R.id.extra)
        val num: TextView? = v.findViewById(R.id.num)
        val more: ImageButton? = v.findViewById(R.id.more)
    }

    fun submit(list: List<Track>) {
        items = list
        notifyDataSetChanged()
    }

    fun setPlaying(id: Long) {
        if (id != playingId) {
            playingId = id
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int) = store.listStyle

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (viewType) {
            1 -> R.layout.item_track_compact
            2 -> R.layout.item_track_detail
            3 -> R.layout.item_track_grid
            4 -> R.layout.item_track_card
            else -> R.layout.item_track
        }
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = items[position]
        val ctx = h.itemView.context
        val style = store.listStyle
        val scale = when (store.textSize) { 0 -> 0.88f; 2 -> 1.15f; else -> 1f }
        val baseTitle = when (style) { 1 -> 14f; 2 -> 15f; 3 -> 13f; 4 -> 15f; else -> 16f }
        val baseSub = when (style) { 1 -> 11f; 3 -> 11f; else -> 12f }
        h.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, baseTitle * scale)
        h.sub.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, baseSub * scale)
        h.extra?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, baseSub * scale)
        h.title.maxLines = if (store.twoLines) 2 else 1
        h.title.text = t.title

        val parts = ArrayList<String>()
        if (store.showArtist) parts.add(t.artist)
        if (style == 2) {
            if (t.album.isNotBlank()) parts.add(t.album)
            h.sub.text = parts.joinToString(" · ")
            val x = ArrayList<String>()
            if (store.showDuration) x.add(fmtTime(t.durationMs))
            if (t.dateAdded > 0) x.add("ajouté le " + dateFmt.format(java.util.Date(t.dateAdded * 1000)))
            h.extra?.text = x.joinToString(" · ")
        } else {
            if (store.showDuration) parts.add(fmtTime(t.durationMs))
            h.sub.text = parts.joinToString(" · ")
        }
        h.sub.visibility = if (h.sub.text.isEmpty()) View.GONE else View.VISIBLE

        h.num?.text = (position + 1).toString()
        h.cover?.let {
            roundCorners(it, ctx.dp(if (style == 3) 14f else 8f))
            ArtLoader.load(ctx, t, it, 160)
            it.visibility = if (store.showCover || style == 3) View.VISIBLE else View.GONE
        }
        val on = t.id == playingId
        h.title.setTextColor(ctx.tc(if (on) R.attr.zAccent else R.attr.zText))
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        h.itemView.setOnLongClickListener { onMore(t, it); true }
        h.more?.setOnClickListener { onMore(t, it) }
    }
}

/** Ligne de liste de groupes : playlist, artiste ou album. */
class Entry(val name: String, val count: Int, val pl: Store.Playlist?, val tracks: List<Track>)

class EntryAdapter(
    private val onClick: (Entry) -> Unit,
    private val onLong: (Entry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    private var items: List<Entry> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.name)
        val count: TextView = v.findViewById(R.id.count)
    }

    fun submit(list: List<Entry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = items[position]
        h.name.text = e.name
        h.count.text = e.count.toString() + " titre(s)"
        h.itemView.setOnClickListener { onClick(e) }
        h.itemView.setOnLongClickListener { onLong(e); true }
    }
}

/** Titre à corriger : ancien nom, nouveau nom (modifiable à la main) et case à cocher. */
class Fix(val t: Track, var newTitle: String, var checked: Boolean = true)

class FixAdapter(private val items: List<Fix>) : RecyclerView.Adapter<FixAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cb: CheckBox = v.findViewById(R.id.fixCheck)
        val old: TextView = v.findViewById(R.id.fixOld)
        val edit: EditText = v.findViewById(R.id.fixNew)
        var watcher: TextWatcher? = null
    }

    fun setAll(on: Boolean) {
        items.forEach { it.checked = on }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_fix, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val f = items[position]
        h.cb.setOnCheckedChangeListener(null)
        h.cb.isChecked = f.checked
        h.cb.setOnCheckedChangeListener { _, on -> f.checked = on }
        h.old.text = f.t.title
        h.watcher?.let { h.edit.removeTextChangedListener(it) }
        h.edit.setText(f.newTitle)
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { f.newTitle = s.toString() }
        }
        h.watcher = w
        h.edit.addTextChangedListener(w)
    }
}
