package com.theveloper.pixelplay.data.network.yandexmusic

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Yandex Music API.
 *
 * Base URL: `https://api.music.yandex.net/`
 *
 * **Note:** The Retrofit instance for this service must use
 * `kotlinx-serialization-converter` (not `GsonConverterFactory`) because
 * the models use `@Serializable` / `@SerialName`.
 *
 * Auth is via `Authorization: OAuth <token>` header — add an OkHttp
 * interceptor when building the Retrofit instance.
 */
interface YandexMusicApiService {

    /**
     * Search across all Yandex Music catalogue types.
     *
     * @param text  Search query text.
     * @param type  Result type filter: `"all"`, `"track"`, `"album"`, `"artist"`, `"podcast"`.
     * @param page  Zero-based page index.
     */
    @GET("search")
    suspend fun search(
        @Query("text") text: String,
        @Query("type") type: String = "all",
        @Query("page") page: Int = 0
    ): Response<YandexSearchResponse>

    /**
     * Get available download variants for a track.
     *
     * The result list contains codec / bitrate / `downloadInfoUrl` entries.
     * To get the actual stream URL you must fetch the XML at `downloadInfoUrl`,
     * then sign it — that logic belongs in the repository layer, not here.
     */
    @GET("tracks/{trackId}/download-info")
    suspend fun getTrackDownloadInfo(
        @Path("trackId") trackId: String
    ): Response<YandexDownloadInfoResponse>

    /**
     * Get artist details by ID.
     */
    @GET("artists/{artistId}")
    suspend fun getArtist(
        @Path("artistId") artistId: String
    ): Response<YandexArtistResponse>

    /**
     * Get an album with its track list (volumes split by disc).
     */
    @GET("albums/{albumId}/with-tracks")
    suspend fun getAlbumWithTracks(
        @Path("albumId") albumId: String
    ): Response<YandexAlbumResponse>

    /**
     * Get the current user's account status (uid, login, display name, avatar).
     */
    @GET("account/status")
    suspend fun getAccountStatus(): Response<YandexAccountStatusResponse>

    /**
     * Get the current user's playlists.
     */
    @GET("users/{userId}/playlists/list")
    suspend fun getUserPlaylists(
        @Path("userId") userId: Long
    ): Response<YandexPlaylistsResponse>

    /**
     * Get a playlist with its tracks.
     */
    @GET("users/{userId}/playlists/{kind}")
    suspend fun getPlaylistWithTracks(
        @Path("userId") userId: Long,
        @Path("kind") kind: Long
    ): Response<YandexPlaylistResponse>
}
