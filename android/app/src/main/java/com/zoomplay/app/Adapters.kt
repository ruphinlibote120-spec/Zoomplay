package com.zoomplay.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrackAdapter(
    private val onClick: (Int) -> Unit,
    private val onMore: (Track, View) -> Unit
) : RecyclerView.Adapter<TrackAdapter.VH>() {

    private var items: List<Track> = emptyList()
    private var playingId: Long = -1L

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView = v.findViewById(R.id.cover)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val more: ImageButton = v.findViewById(R.id.more)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = items[position]
        val ctx = h.itemView.context
        roundCorners(h.cover, ctx.dp(8f))
        ArtLoader.load(ctx, t, h.cover, 160)
        h.title.text = t.title
        h.sub.text = t.artist + " · " + fmtTime(t.durationMs)
        val on = t.id == playingId
        h.title.setTextColor(ctx.getColor(if (on) R.color.accent else R.color.text))
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        h.more.setOnClickListener { onMore(t, it) }
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
