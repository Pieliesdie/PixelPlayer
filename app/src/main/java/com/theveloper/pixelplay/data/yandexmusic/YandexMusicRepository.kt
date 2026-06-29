package com.theveloper.pixelplay.data.yandexmusic

import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.YandexMusicDao
import com.theveloper.pixelplay.data.database.YandexMusicPlaylistEntity
import com.theveloper.pixelplay.data.database.YandexMusicSongEntity
import com.theveloper.pixelplay.data.database.serializeArtistRefs
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.yandexmusic.YandexMusicApiService
import com.theveloper.pixelplay.data.network.yandexmusic.YandexMusicManager
import com.theveloper.pixelplay.data.network.yandexmusic.YandexPlaylist
import com.theveloper.pixelplay.data.network.yandexmusic.YandexTokenStore
import com.theveloper.pixelplay.data.network.yandexmusic.yandexCoverUrl
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.stream.BulkSyncResult
import com.theveloper.pixelplay.data.stream.CloudMusicUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class YandexMusicRepository @Inject constructor(
    private val api: YandexMusicApiService,
    private val manager: YandexMusicManager,
    private val dao: YandexMusicDao,
    private val musicDao: MusicDao,
    private val tokenStore: YandexTokenStore,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) {
    private companion object {
        private const val YANDEX_SONG_ID_OFFSET = 9_000_000_000_000L
        private const val YANDEX_ALBUM_ID_OFFSET = 10_000_000_000_000L
        private const val YANDEX_ARTIST_ID_OFFSET = 11_000_000_000_000L
        private const val YANDEX_PARENT_DIRECTORY = "/Cloud/YandexMusic"
        private const val YANDEX_GENRE = "Yandex Music"
        private const val YANDEX_PLAYLIST_PREFIX = "yandex_playlist:"
    }

    val isLoggedInFlow: StateFlow<Boolean> = manager.isLoggedInFlow
    val isLoggedIn: Boolean get() = manager.isLoggedIn
    val userNickname: String? get() = manager.nickname
    val userAvatarUrl: String? get() = manager.avatarUrl

    // ─── Auth ─────────────────────────────────────────────────────────

    suspend fun logout() {
        manager.logout()
        dao.clearAllSongs()
        dao.clearAllPlaylists()
        musicDao.clearAllYandexSongs()
    }

    // ─── Content ──────────────────────────────────────────────────────

    fun getPlaylists(): Flow<List<YandexMusicPlaylistEntity>> = dao.getAllPlaylists()

    fun getPlaylistSongs(playlistKind: String): Flow<List<Song>> {
        return dao.getSongsByPlaylist(playlistKind).map { entities ->
            entities.map { it.toSong() }
        }
    }

    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllYandexSongs().map { entities ->
            entities.map { it.toSong() }
        }
    }

    suspend fun syncUserPlaylists(): Result<List<YandexMusicPlaylistEntity>> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))
        val uid = manager.userId ?: return Result.failure(Exception("User ID not available"))

        return withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getUserPlaylists(uid)
                if (!response.isSuccessful) {
                    throw RuntimeException("getUserPlaylists failed: ${response.code()} ${response.message()}")
                }
                val remotePlaylists = response.body()?.result?.toMutableList() ?: mutableListOf()

                // "I Like" (Мне нравится) is a system playlist (kind = 3) that is usually omitted from getUserPlaylists.
                if (remotePlaylists.none { it.kind == 3L }) {
                      val existingIlike = dao.getAllPlaylistsList().find { it.kind == "3" }
                      remotePlaylists.add(0, YandexPlaylist(
                          kind = 3L,
                          title = existingIlike?.title ?: "Мне нравится",
                          trackCount = existingIlike?.songCount ?: 0
                      ))
                }

                val entities = remotePlaylists.map { pl ->
                    val coverUrl = pl.cover?.uri?.let { yandexCoverUrl(it) }
                    YandexMusicPlaylistEntity(
                        kind = pl.kind.toString(),
                        title = pl.title,
                        coverUrl = coverUrl,
                        songCount = pl.trackCount,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteKinds = entities.map { it.kind }.toSet()
                localPlaylists.filter { it.kind !in remoteKinds }.forEach { stale ->
                    dao.deleteSongsByPlaylist(stale.kind)
                    dao.deletePlaylist(stale.kind)
                    deleteAppPlaylistForYandexPlaylist(stale.kind)
                }

                entities.forEach { dao.insertPlaylist(it) }
                entities
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistKind: String): Result<Int> {
        return syncPlaylistSongs(playlistKind, syncUnifiedLibrary = true)
    }

    suspend fun syncPlaylistSongs(
        playlistKind: String,
        syncUnifiedLibrary: Boolean
    ): Result<Int> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))
        val uid = manager.userId ?: return Result.failure(Exception("User ID not available"))

        return withContext(Dispatchers.IO) {
            runCatching {
                val kind = playlistKind.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid playlist kind: $playlistKind")

                val response = api.getPlaylistWithTracks(uid, kind)
                if (!response.isSuccessful) {
                    throw RuntimeException("getPlaylistWithTracks failed: ${response.code()} ${response.message()}")
                }
                val playlist = response.body()?.result
                    ?: throw IllegalStateException("Empty playlist response")

                val playlistTitle = playlist.title
                val trackEntries = playlist.tracks
                val entities = trackEntries.mapNotNull { entry ->
                    val track = entry.track ?: return@mapNotNull null
                    if (track.id.isBlank()) return@mapNotNull null
                    parseTrackToEntity(track, playlistKind)
                }

                // Update the track count in the dashboard
                dao.updatePlaylistTrackCount(playlistKind, entities.size)

                // 1. Delete old songs for this playlist
                dao.deleteSongsByPlaylist(playlistKind)
                dao.insertSongs(entities)

                updateAppPlaylistForYandexPlaylist(playlistKind, playlistTitle, entities)

                if (syncUnifiedLibrary) {
                    syncUnifiedLibrarySongsFromYandexMusic()
                }

                Timber.d("Synced ${entities.size} songs for Yandex playlist $playlistKind")
                entities.size
            }
        }
    }

    suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            val playlistResult = syncUserPlaylists().getOrElse {
                return@withContext Result.failure(it)
            }
            if (playlistResult.isEmpty()) {
                syncUnifiedLibrarySongsFromYandexMusic()
                return@withContext Result.success(
                    BulkSyncResult(playlistCount = 0, syncedSongCount = 0, failedPlaylistCount = 0)
                )
            }

            var syncedSongCount = 0
            var failedPlaylistCount = 0

            playlistResult.forEach { playlist ->
                syncPlaylistSongs(playlist.kind, syncUnifiedLibrary = false).fold(
                    onSuccess = { count -> syncedSongCount += count },
                    onFailure = {
                        failedPlaylistCount += 1
                        Timber.w(it, "Failed syncing Yandex playlist kind=${playlist.kind}")
                    }
                )
            }

            syncUnifiedLibrarySongsFromYandexMusic()

            Result.success(
                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            )
        }
    }

    // ─── Entity Parsing ───────────────────────────────────────────────

    private fun parseTrackToEntity(
        track: com.theveloper.pixelplay.data.network.yandexmusic.YandexTrack,
        playlistKind: String
    ): YandexMusicSongEntity {
        val artistName = track.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
        val albumName = track.albums.firstOrNull()?.title ?: "Unknown Album"
        val albumCoverUri = track.albums.firstOrNull()?.coverUri
        val coverUrl = if (!albumCoverUri.isNullOrBlank()) yandexCoverUrl(albumCoverUri) else null

        val genre = track.albums.firstOrNull()?.genre?.ifBlank { null }

        return YandexMusicSongEntity(
            id = "${playlistKind}_${track.id}",
            yandexId = track.id,
            playlistKind = playlistKind,
            title = track.title,
            artist = artistName,
            album = albumName,
            duration = track.durationMs,
            albumArtUrl = coverUrl,
            mimeType = "audio/mpeg",
            bitrate = null,
            dateAdded = System.currentTimeMillis(),
            genre = genre
        )
    }

    // ─── Unified Library Sync ─────────────────────────────────────────

    private suspend fun syncUnifiedLibrarySongsFromYandexMusic() {
        val yandexSongs = dao.getAllYandexSongsList()
        val existingUnifiedIds = musicDao.getAllYandexSongIds()

        if (yandexSongs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) {
                musicDao.clearAllYandexSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(yandexSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        yandexSongs.forEach { yandexSong ->
            val songId = toUnifiedSongId(yandexSong.yandexId)
            val artistNames = parseArtistNames(yandexSong.artist)
            val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
            val primaryArtistId = toUnifiedArtistId(primaryArtistName)

            artistNames.forEachIndexed { index, artistName ->
                val artistId = toUnifiedArtistId(artistName)
                artists.putIfAbsent(
                    artistId,
                    ArtistEntity(id = artistId, name = artistName, trackCount = 0, imageUrl = null)
                )
                crossRefs.add(
                    SongArtistCrossRef(songId = songId, artistId = artistId, isPrimary = index == 0)
                )
            }

            val yandexArtistRefs = artistNames.mapIndexed { index, artistName ->
                ArtistRef(id = toUnifiedArtistId(artistName), name = artistName, isPrimary = index == 0)
            }

            val albumId = toUnifiedAlbumId(yandexSong.album)
            val albumName = yandexSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = yandexSong.dateAdded,
                    year = 0,
                    albumArtUriString = yandexSong.albumArtUrl
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = yandexSong.title,
                    artistName = yandexSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "yandexmusic://${yandexSong.yandexId}",
                    albumArtUriString = yandexSong.albumArtUrl,
                    duration = yandexSong.duration,
                    genre = yandexSong.genre ?: YANDEX_GENRE,
                    filePath = "",
                    parentDirectoryPath = YANDEX_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = yandexSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = yandexSong.mimeType,
                    bitrate = yandexSong.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    artistsJson = serializeArtistRefs(yandexArtistRefs),
                    sourceType = SourceType.YANDEX_MUSIC
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedIds = songs.map { it.id }.toSet()
        val deletedUnifiedIds = existingUnifiedIds.filter { it !in currentUnifiedIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedIds
        )
    }

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    private fun toUnifiedSongId(yandexId: String): Long {
        val hash = yandexId.hashCode().toLong().absoluteValue
        return -(YANDEX_SONG_ID_OFFSET + hash)
    }

    private fun toUnifiedAlbumId(albumName: String): Long {
        val hash = albumName.lowercase().hashCode().toLong().absoluteValue
        return -(YANDEX_ALBUM_ID_OFFSET + hash)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(YANDEX_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    // ─── App Playlist Management ──────────────────────────────────────

    private fun getAppPlaylistIdForYandex(playlistKind: String): String {
        return "$YANDEX_PLAYLIST_PREFIX$playlistKind"
    }

    private suspend fun updateAppPlaylistForYandexPlaylist(
        playlistKind: String,
        playlistName: String,
        entities: List<YandexMusicSongEntity>
    ) {
        try {
            val unifiedSongIds = entities.map { toUnifiedSongId(it.yandexId).toString() }
            val appPlaylistId = getAppPlaylistIdForYandex(playlistKind)

            val existingPlaylist = withContext(Dispatchers.IO) {
                playlistPreferencesRepository.userPlaylistsFlow.map { playlists ->
                    playlists.find { it.id == appPlaylistId }
                }.first()
            }

            if (existingPlaylist != null) {
                playlistPreferencesRepository.updatePlaylist(
                    existingPlaylist.copy(
                        name = playlistName,
                        songIds = unifiedSongIds,
                        lastModified = System.currentTimeMillis(),
                        source = "YANDEX_MUSIC"
                    )
                )
            } else {
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,
                    source = "YANDEX_MUSIC"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/create app playlist for Yandex playlist $playlistKind")
        }
    }

    private suspend fun deleteAppPlaylistForYandexPlaylist(playlistKind: String) {
        try {
            val appPlaylistId = getAppPlaylistIdForYandex(playlistKind)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Yandex playlist $playlistKind")
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────

    suspend fun deletePlaylist(playlistKind: String) {
        dao.deleteSongsByPlaylist(playlistKind)
        dao.deletePlaylist(playlistKind)
        deleteAppPlaylistForYandexPlaylist(playlistKind)
        syncUnifiedLibrarySongsFromYandexMusic()
    }
}
