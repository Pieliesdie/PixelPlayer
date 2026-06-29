package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song

@Entity(
    tableName = "yandex_songs",
    indices = [
        Index(value = ["yandex_id"]),
        Index(value = ["playlist_kind"]),
        Index(value = ["playlist_kind", "date_added"])
    ]
)
data class YandexMusicSongEntity(
    @PrimaryKey val id: String,                          // Composite: "${playlistKind}_${yandexId}"
    @ColumnInfo(name = "yandex_id") val yandexId: String,
    @ColumnInfo(name = "playlist_kind") val playlistKind: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,                                   // milliseconds
    @ColumnInfo(name = "album_art_url") val albumArtUrl: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val bitrate: Int?,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    val genre: String? = null
)

fun YandexMusicSongEntity.toSong(): Song {
    return Song(
        id = "yandex_$yandexId",
        title = title,
        artist = artist,
        artistId = -1L,
        album = album,
        albumId = -1L,
        path = "",
        contentUriString = "yandexmusic://$yandexId",
        albumArtUriString = albumArtUrl,
        duration = duration,
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = 0,
        year = 0,
        trackNumber = 0,
        dateAdded = dateAdded,
        genre = genre,
        isFavorite = false
    )
}
