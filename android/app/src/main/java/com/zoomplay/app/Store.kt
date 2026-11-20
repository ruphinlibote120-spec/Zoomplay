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
