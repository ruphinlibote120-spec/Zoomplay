package com.zoomplay.app

import android.content.Context
import android.provider.MediaStore

/** Lit tous les fichiers audio du téléphone via MediaStore (nouveaux fichiers compris). */
object Library {
    fun scan(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val a = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )
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
                while (c.moveToNext()) {
                    val artist = c.getString(iArtist)
                    val album = c.getString(iAlbum)
                    out.add(
                        Track(
                            id = c.getLong(iId),
                            title = c.getString(iTitle) ?: "Sans titre",
                            artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Artiste inconnu" else artist,
                            album = if (album.isNullOrBlank() || album == "<unknown>") "" else album,
                            albumId = c.getLong(iAlbumId),
                            durationMs = c.getLong(iDur),
                            dateAdded = c.getLong(iDate)
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
