package com.zoomplay.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/**
 * Sauvegarde / restauration de tout ce qui est à toi : playlists, favoris, statistiques, infos modifiées,
 * pochettes, réglages et dernière file de lecture. Les chansons sont retrouvées par leur nom de fichier
 * et leur durée, donc la restauration marche aussi sur un autre téléphone.
 */
object BackupManager {
    class Result(val playlists: Int, val favorites: Int, val matched: Int, val missing: Int)

    fun export(ctx: Context, store: Store, all: List<Track>, out: OutputStream) {
        val byId = all.associateBy { it.id }
        val fav = store.favorites()
        val pls = store.playlists()
        val stats = store.stats()
        val titleOv = store.titleOverrides()
        val meta = store.metaOverrides()
        val queue = (store.prefs.getString("queue", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }

        val used = HashSet<Long>()
        used.addAll(fav); pls.forEach { used.addAll(it.ids) }
        used.addAll(stats.keys); used.addAll(titleOv.keys); used.addAll(meta.keys); used.addAll(queue)

        val root = JSONObject()
        root.put("app", "Zoomplay")
        root.put("version", 1)

        val st = JSONObject()
        st.put("theme", store.theme); st.put("sortMode", store.sortMode); st.put("vizStyle", store.vizStyle)
        st.put("listStyle", store.listStyle); st.put("textSize", store.textSize)
        st.put("showCover", store.showCover); st.put("showDuration", store.showDuration)
        st.put("showArtist", store.showArtist); st.put("twoLines", store.twoLines)
        st.put("eqBoost", store.eqBoost); st.put("eqPreset", store.eqPreset)
        st.put("eqBands", store.eqBands.joinToString(","))
        root.put("settings", st)

        val tr = JSONObject()
        for (id in used) {
            val t = byId[id] ?: continue
            tr.put(id.toString(), JSONObject().put("n", t.displayName).put("t", t.title).put("a", t.artist).put("d", t.durationMs))
        }
        root.put("tracks", tr)

        root.put("fav", JSONArray(fav.toList()))
        val pa = JSONArray()
        for (p in pls) pa.put(JSONObject().put("name", p.name).put("ids", JSONArray(p.ids)))
        root.put("playlists", pa)

        val so = JSONObject()
        for ((id, v) in stats) so.put(id.toString(), JSONArray().put(v[0]).put(v[1]))
        root.put("stats", so)

        val to = JSONObject()
        for ((id, v) in titleOv) to.put(id.toString(), v)
        root.put("titleOv", to)

        val covers = ArrayList<File>()
        val mo = JSONObject()
        for ((id, m) in meta) {
            val j = JSONObject()
            m.artist?.let { j.put("artist", it) }
            m.album?.let { j.put("album", it) }
            m.year?.let { j.put("year", it) }
            m.cover?.let { c ->
                val f = File(c)
                if (f.exists()) { j.put("cover", f.name); covers.add(f) }
            }
            if (j.length() > 0) mo.put(id.toString(), j)
        }
        root.put("meta", mo)

        root.put("queue", JSONArray(queue))
        root.put("qIndex", store.prefs.getInt("qIndex", 0))
        root.put("qShuffle", store.prefs.getBoolean("qShuffle", false))
        root.put("qRepeat", store.prefs.getInt("qRepeat", 0))
        root.put("qSpeed", store.prefs.getFloat("qSpeed", 1f).toDouble())

        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("backup.json"))
            z.write(root.toString().toByteArray(Charsets.UTF_8))
            z.closeEntry()
            for (f in covers) {
                z.putNextEntry(ZipEntry("covers/" + f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    fun restore(ctx: Context, store: Store, all: List<Track>, input: InputStream): Result {
        var json: JSONObject? = null
        val coverBytes = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val bytes = z.readBytes()
                if (e.name == "backup.json") json = JSONObject(String(bytes, Charsets.UTF_8))
                else if (e.name.startsWith("covers/")) coverBytes[e.name.removePrefix("covers/")] = bytes
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val root = json ?: throw IllegalArgumentException("Fichier de sauvegarde invalide")
        if (root.optString("app") != "Zoomplay") throw IllegalArgumentException("Ce n'est pas une sauvegarde Zoomplay")

        val tracks = root.optJSONObject("tracks") ?: JSONObject()
        val byName = all.groupBy { it.displayName.lowercase() }
        val byTitle = all.groupBy { (it.title + "|" + it.artist).lowercase() }
        val idSet = all.map { it.id }.toHashSet()
        var matched = 0
        var missing = 0
        val cache = HashMap<Long, Long?>()
        fun map(old: Long): Long? = cache.getOrPut(old) {
            val info = tracks.optJSONObject(old.toString())
            val r: Long? = if (info == null) {
                if (idSet.contains(old)) old else null
            } else {
                val d = info.optLong("d")
                val c1 = byName[info.optString("n").lowercase()]
                val c2 = byTitle[(info.optString("t") + "|" + info.optString("a")).lowercase()]
                val cand = (c1 ?: c2)
                cand?.minByOrNull { abs(it.durationMs - d) }?.takeIf { c1 != null || abs(it.durationMs - d) < 3000 }?.id
            }
            if (r != null) matched++ else missing++
            r
        }

        // réglages
        root.optJSONObject("settings")?.let { s ->
            store.theme = s.optInt("theme", store.theme)
            store.sortMode = s.optInt("sortMode", store.sortMode)
            store.vizStyle = s.optInt("vizStyle", store.vizStyle)
            store.listStyle = s.optInt("listStyle", store.listStyle)
            store.textSize = s.optInt("textSize", store.textSize)
            store.showCover = s.optBoolean("showCover", store.showCover)
            store.showDuration = s.optBoolean("showDuration", store.showDuration)
            store.showArtist = s.optBoolean("showArtist", store.showArtist)
            store.twoLines = s.optBoolean("twoLines", store.twoLines)
            store.eqBoost = s.optInt("eqBoost", store.eqBoost)
            store.eqPreset = s.optString("eqPreset", store.eqPreset)
            val bands = s.optString("eqBands", "").split(",").mapNotNull { it.trim().toIntOrNull() }
            if (bands.size == 5) store.eqBands = bands.toIntArray()
        }

        // favoris
        val favs = LinkedHashSet<Long>()
        root.optJSONArray("fav")?.let { a -> for (i in 0 until a.length()) map(a.getLong(i))?.let { favs.add(it) } }
        store.prefs.edit().putString("fav", favs.joinToString(",")).apply()

        // playlists
        val pls = ArrayList<Store.Playlist>()
        root.optJSONArray("playlists")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ids = ArrayList<Long>()
                val ja = o.getJSONArray("ids")
                for (k in 0 until ja.length()) map(ja.getLong(k))?.let { if (!ids.contains(it)) ids.add(it) }
                pls.add(Store.Playlist((System.currentTimeMillis() + i).toString(), o.getString("name"), ids))
            }
        }
        store.savePlaylists(pls)

        // statistiques
        val stats = JSONObject()
        root.optJSONObject("stats")?.let { o ->
            for (k in o.keys()) {
                val id = map(k.toLong()) ?: continue
                val a = o.getJSONArray(k)
                val cur = stats.optJSONArray(id.toString())
                val c = a.getLong(0) + (cur?.getLong(0) ?: 0L)
                val l = maxOf(a.getLong(1), cur?.getLong(1) ?: 0L)
                stats.put(id.toString(), JSONArray().put(c).put(l))
            }
        }
        store.prefs.edit().putString("stats", stats.toString()).apply()

        // titres corrigés
        val tov = HashMap<Long, String>()
        root.optJSONObject("titleOv")?.let { o -> for (k in o.keys()) map(k.toLong())?.let { tov[it] = o.getString(k) } }
        store.setTitleOverrides(tov)

        // infos modifiées + pochettes
        val dir = File(ctx.filesDir, "covers")
        dir.mkdirs()
        val mov = HashMap<Long, Store.Meta>()
        root.optJSONObject("meta")?.let { o ->
            for (k in o.keys()) {
                val id = map(k.toLong()) ?: continue
                val j = o.getJSONObject(k)
                var cover: String? = null
                if (j.has("cover")) {
                    val bytes = coverBytes[j.getString("cover")]
                    if (bytes != null) {
                        val f = File(dir, id.toString() + "_" + System.currentTimeMillis() + ".jpg")
                        f.writeBytes(bytes)
                        cover = f.absolutePath
                    }
                }
                mov[id] = Store.Meta(
                    if (j.has("artist")) j.getString("artist") else null,
                    if (j.has("album")) j.getString("album") else null,
                    if (j.has("year")) j.getInt("year") else null,
                    cover
                )
            }
        }
        store.setMetaOverrides(mov)

        // dernière file de lecture
        val q = ArrayList<Long>()
        root.optJSONArray("queue")?.let { a -> for (i in 0 until a.length()) map(a.getLong(i))?.let { q.add(it) } }
        if (q.isNotEmpty()) {
            store.prefs.edit()
                .putString("queue", q.joinToString(","))
                .putInt("qIndex", root.optInt("qIndex", 0).coerceIn(0, q.size - 1))
                .putLong("qPos", 0L)
                .putBoolean("qShuffle", root.optBoolean("qShuffle", false))
                .putInt("qRepeat", root.optInt("qRepeat", 0))
                .putFloat("qSpeed", root.optDouble("qSpeed", 1.0).toFloat())
                .apply()
        }
        return Result(pls.size, favs.size, matched, missing)
    }
}
