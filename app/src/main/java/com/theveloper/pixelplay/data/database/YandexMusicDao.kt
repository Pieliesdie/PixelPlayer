package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface YandexMusicDao {

    // ─── Songs ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM yandex_songs ORDER BY date_added DESC")
    fun getAllYandexSongs(): Flow<List<YandexMusicSongEntity>>

    @Query("SELECT * FROM yandex_songs ORDER BY date_added DESC")
    suspend fun getAllYandexSongsList(): List<YandexMusicSongEntity>

    @Query("SELECT COUNT(*) FROM yandex_songs")
    suspend fun getYandexCount(): Int

    @Query("SELECT * FROM yandex_songs WHERE playlist_kind = :playlistKind ORDER BY date_added DESC")
    fun getSongsByPlaylist(playlistKind: String): Flow<List<YandexMusicSongEntity>>

    @Query("SELECT * FROM yandex_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<YandexMusicSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<YandexMusicSongEntity>)

    @Query("DELETE FROM yandex_songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM yandex_songs WHERE playlist_kind = :playlistKind")
    suspend fun deleteSongsByPlaylist(playlistKind: String)

    // ─── Playlists ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: YandexMusicPlaylistEntity)

    @Query("UPDATE yandex_playlists SET song_count = :count WHERE kind = :kind")
    suspend fun updatePlaylistTrackCount(kind: String, count: Int)

    @Query("SELECT * FROM yandex_playlists ORDER BY title ASC")
    fun getAllPlaylists(): Flow<List<YandexMusicPlaylistEntity>>

    @Query("SELECT * FROM yandex_playlists")
    suspend fun getAllPlaylistsList(): List<YandexMusicPlaylistEntity>

    @Query("DELETE FROM yandex_playlists WHERE kind = :kind")
    suspend fun deletePlaylist(kind: String)

    // ─── Clear All ─────────────────────────────────────────────────────

    @Query("DELETE FROM yandex_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM yandex_playlists")
    suspend fun clearAllPlaylists()
}
