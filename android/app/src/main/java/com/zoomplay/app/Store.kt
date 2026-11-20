package com.zoomplay.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Réglages, favoris et playlists, enregistrés sur l'appareil. */
class Store(ctx: Context) {
    class Playlist(val id: String, var name: String, val ids: MutableList<Long>)

    val prefs = ctx.applicationContext.getSharedPreferences("zoomplay", Context.MODE_PRIVATE)

    var micOn: Boolean
        get() = prefs.getBoolean("micOn", false)
        set(v) { prefs.edit().putBoolean("micOn", v).apply() }

    var eqBoost: Int
        get() = prefs.getInt("eqBoost", 100)
        set(v) { prefs.edit().putInt("eqBoost", v).apply() }

    var eqPreset: String
        get() = prefs.getString("eqPreset", "Normal") ?: "Normal"
        set(v) { prefs.edit().putString("eqPreset", v).apply() }

    var eqBands: IntArray
        get() {
            val s = prefs.getString("eqBands", "0,0,0,0,0") ?: "0,0,0,0,0"
            val p = s.split(",").mapNotNull { it.trim().toIntOrNull() }
            return if (p.size == 5) p.toIntArray() else IntArray(5)
        }
        set(v) { prefs.edit().putString("eqBands", v.joinToString(",")).apply() }

    var theme: Int
        get() = prefs.getInt("theme", 0)
        set(v) { prefs.edit().putInt("theme", v).apply() }

    var vizStyle: Int
        get() = prefs.getInt("vizStyle", 0)
        set(v) { prefs.edit().putInt("vizStyle", v).apply() }

    var vizAsked: Boolean
        get() = prefs.getBoolean("vizAsked", false)
        set(v) { prefs.edit().putBoolean("vizAsked", v).apply() }

    /** Titres corrigés par l'utilisateur : Android peut remettre l'ancien titre (tag du fichier) au rescan, ceux-ci priment. */
    fun titleOverrides(): Map<Long, String> {
        val out = HashMap<Long, String>()
        try {
            val o = JSONObject(prefs.getString("titleOv", "{}") ?: "{}")
            for (k in o.keys()) k.toLongOrNull()?.let { out[it] = o.getString(k) }
        } catch (e: Exception) {
        }
        return out
    }

    fun setTitleOverrides(m: Map<Long, String>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k.toString(), v)
        prefs.edit().putString("titleOv", o.toString()).apply()
    }

    /** Infos modifiées dans Zoomplay (artiste, album, année, pochette) : elles priment sur celles du fichier. */
    class Meta(var artist: String? = null, var album: String? = null, var year: Int? = null, var cover: String? = null)

    fun metaOverrides(): MutableMap<Long, Meta> {
        val out = HashMap<Long, Meta>()
        try {
            val o = JSONObject(prefs.getString("metaOv", "{}") ?: "{}")
            for (k in o.keys()) {
                val id = k.toLongOrNull() ?: continue
                val j = o.getJSONObject(k)
                out[id] = Meta(
                    if (j.has("artist")) j.getString("artist") else null,
                    if (j.has("album")) j.getString("album") else null,
                    if (j.has("year")) j.getInt("year") else null,
                    if (j.has("cover")) j.getString("cover") else null
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun setMetaOverrides(m: Map<Long, Meta>) {
        val o = JSONObject()
        for ((id, v) in m) {
            val j = JSONObject()
            v.artist?.let { j.put("artist", it) }
            v.album?.let { j.put("album", it) }
            v.year?.let { j.put("year", it) }
            v.cover?.let { j.put("cover", it) }
            if (j.length() > 0) o.put(id.toString(), j)
        }
        prefs.edit().putString("metaOv", o.toString()).apply()
    }

    /** Statistiques d'écoute : id -> [nombre d'écoutes, dernière écoute en ms]. */
    fun stats(): Map<Long, LongArray> {
        val out = HashMap<Long, LongArray>()
        try {
            val o = JSONObject(prefs.getString("stats", "{}") ?: "{}")
            for (k in o.keys()) {
                val id = k.toLongOrNull() ?: continue
                val a = o.getJSONArray(k)
                out[id] = longArrayOf(a.getLong(0), a.getLong(1))
            }
        } catch (e: Exception) {
        }
        return out
    }

    @Synchronized
    fun bumpPlay(id: Long) {
        val m = HashMap(stats())
        val cur = m[id]
        m[id] = longArrayOf((cur?.get(0) ?: 0L) + 1L, System.currentTimeMillis())
        val o = JSONObject()
        for ((k, v) in m) o.put(k.toString(), JSONArray().put(v[0]).put(v[1]))
        prefs.edit().putString("stats", o.toString()).apply()
    }

    var listStyle: Int
        get() = prefs.getInt("listStyle", 0)
        set(v) { prefs.edit().putInt("listStyle", v).apply() }

    var textSize: Int
        get() = prefs.getInt("textSize", 1)
        set(v) { prefs.edit().putInt("textSize", v).apply() }

    var showCover: Boolean
        get() = prefs.getBoolean("showCover", true)
        set(v) { prefs.edit().putBoolean("showCover", v).apply() }

    var showDuration: Boolean
        get() = prefs.getBoolean("showDuration", true)
        set(v) { prefs.edit().putBoolean("showDuration", v).apply() }

    var showArtist: Boolean
        get() = prefs.getBoolean("showArtist", true)
        set(v) { prefs.edit().putBoolean("showArtist", v).apply() }

    var twoLines: Boolean
        get() = prefs.getBoolean("twoLines", false)
        set(v) { prefs.edit().putBoolean("twoLines", v).apply() }

    var sortMode: Int
        get() = prefs.getInt("sortMode", 0)
        set(v) { prefs.edit().putInt("sortMode", v).apply() }

    var sleepAt: Long
        get() = prefs.getLong("sleepAt", 0L)
        set(v) { prefs.edit().putLong("sleepAt", v).apply() }

    fun favorites(): MutableSet<Long> {
        val out = HashSet<Long>()
        val s = prefs.getString("fav", "") ?: ""
        s.split(",").forEach { it.toLongOrNull()?.let { id -> out.add(id) } }
        return out
    }

    fun isFav(id: Long) = favorites().contains(id)

    fun toggleFav(id: Long): Boolean {
        val f = favorites()
        val now = if (f.contains(id)) { f.remove(id); false } else { f.add(id); true }
        prefs.edit().putString("fav", f.joinToString(",")).apply()
        return now
    }

    fun playlists(): MutableList<Playlist> {
        val out = ArrayList<Playlist>()
        try {
            val arr = JSONArray(prefs.getString("playlists", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = ArrayList<Long>()
                val ja = o.getJSONArray("ids")
                for (k in 0 until ja.length()) ids.add(ja.getLong(k))
                out.add(Playlist(o.getString("id"), o.getString("name"), ids))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            val ja = JSONArray()
            p.ids.forEach { ja.put(it) }
            o.put("ids", ja)
            arr.put(o)
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }
}
