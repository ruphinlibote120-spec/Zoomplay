package com.zoomplay.app

import java.text.Normalizer
import kotlin.math.abs

/** Détecte les titres en double : même nom (sans « (256k) », « (0) »…) et même durée à 2 s près. */
object Duplicates {
    private val accents = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val bitrate = Regex("\\(\\s*\\d{2,3}\\s*k\\s*\\)")
    private val copyMark = Regex("\\(\\s*\\d{1,2}\\s*\\)\\s*$")
    private val nonAlnum = Regex("[^a-z0-9]+")

    fun key(title: String): String {
        var s = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD).replace(accents, "")
        s = s.replace(bitrate, "").trim()
        s = s.replace(copyMark, "")
        return s.replace(nonAlnum, "")
    }

    /** Chaque groupe est trié du plus ancien (à garder) au plus récent. */
    fun find(tracks: List<Track>): List<List<Track>> {
        val out = ArrayList<List<Track>>()
        tracks.groupBy { key(it.title) }.forEach { (k, list) ->
            if (k.isEmpty() || list.size < 2) return@forEach
            val byDur = list.sortedBy { it.durationMs }
            var cur = ArrayList<Track>()
            for (t in byDur) {
                if (cur.isNotEmpty() && abs(t.durationMs - cur.last().durationMs) > 2000) {
                    if (cur.size >= 2) out.add(cur.sortedBy { it.dateAdded })
                    cur = ArrayList()
                }
                cur.add(t)
            }
            if (cur.size >= 2) out.add(cur.sortedBy { it.dateAdded })
        }
        return out.sortedBy { key(it[0].title) }
    }
}
