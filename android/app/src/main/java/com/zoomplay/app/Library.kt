package com.zoomplay.app

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/** Lit tous les fichiers audio du téléphone via MediaStore (nouveaux fichiers compris). */
object Library {
    fun scan(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val st = Store(ctx)
        val ov = st.titleOverrides()
        val mo = st.metaOverrides()
        val a = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val withPath = Build.VERSION.SDK_INT < 29
        val base = arrayListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.YEAR
        )
        @Suppress("DEPRECATION")
        if (withPath) base.add(MediaStore.Audio.Media.DATA)
        if (Build.VERSION.SDK_INT >= 29) base.add(MediaStore.Audio.Media.RELATIVE_PATH)
        val proj = base.toTypedArray()
        val sel = "${MediaStore.Audio.Media.DURATION} >= 10000 AND " +
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0"
        try {
            ctx.contentResolver.query(a, proj, sel, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iRel = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH) else -1
                @Suppress("DEPRECATION")
                val iPath = if (withPath) c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val pth = if (iPath >= 0) c.getString(iPath) ?: "" else ""
                    var folder = if (iRel >= 0) (c.getString(iRel) ?: "") else pth.substringBeforeLast('/', "").removePrefix("/storage/emulated/0/").removePrefix("/sdcard/")
                    folder = folder.trim('/')
                    if (folder.isEmpty()) folder = "Racine"
                    val m = mo[id]
                    val artist = m?.artist ?: c.getString(iArtist)
                    val album = m?.album ?: c.getString(iAlbum)
                    out.add(
                        Track(
                            id = id,
                            title = ov[c.getLong(iId)] ?: c.getString(iTitle) ?: "Sans titre",
                            artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Artiste inconnu" else artist,
                            album = if (album.isNullOrBlank() || album == "<unknown>") "" else album,
                            albumId = c.getLong(iAlbumId),
                            durationMs = c.getLong(iDur),
                            dateAdded = c.getLong(iDate),
                            displayName = c.getString(iName) ?: "",
                            path = if (iPath >= 0) c.getString(iPath) ?: "" else "",
                            year = m?.year ?: c.getInt(iYear),
                            cover = m?.cover ?: "",
                            folder = folder
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // permission refusée ou MediaStore indisponible : liste vide
        }
        return out
    }
}
