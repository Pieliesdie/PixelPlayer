package com.theveloper.pixelplay.data.network.yandexmusic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Yandex Music API operations.
 *
 * Handles the multi-step process of resolving direct streaming URLs
 * from track download info, including the XML-based signing flow.
 * Also manages authentication state via [YandexTokenStore].
 */
@Singleton
class YandexMusicManager @Inject constructor(
    private val api: YandexMusicApiService,
    private val okHttpClient: OkHttpClient,
    private val tokenStore: YandexTokenStore
) {
    private val _isLoggedInFlow = MutableStateFlow(tokenStore.isLoggedIn)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn

    // Cached account info
    @Volatile private var cachedUserId: Long? = null
    @Volatile private var cachedNickname: String? = null
    @Volatile private var cachedAvatarUrl: String? = null

    val userId: Long? get() = cachedUserId
    val nickname: String? get() = cachedNickname
    val avatarUrl: String? get() = cachedAvatarUrl

    init {
        // Try to fetch account status on creation if a token is saved
        if (tokenStore.isLoggedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                fetchAccountStatus().onFailure {
                    Timber.w(it, "YandexMusicManager: Failed to restore account status from saved token")
                }
            }
        }
    }

    fun logout() {
        tokenStore.clearToken()
        cachedUserId = null
        cachedNickname = null
        cachedAvatarUrl = null
        _isLoggedInFlow.value = false
    }

    /**
     * Fetch the current user's account status and cache uid, nickname, avatar.
     * Call this after login to populate the cached fields.
     */
    suspend fun fetchAccountStatus(): Result<YandexAccountStatus> = runCatching {
        val response = api.getAccountStatus()
        if (response.isSuccessful) {
            val status = response.body()?.result?.account ?: throw IllegalStateException("Empty account status")
            cachedUserId = status.uid
            cachedNickname = status.nickname
            cachedAvatarUrl = status.avatarUrl
            _isLoggedInFlow.value = true
            status
        } else {
            throw RuntimeException("Account status failed: ${response.code()} ${response.message()}")
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    suspend fun search(
        text: String,
        type: String = "all",
        page: Int = 0
    ): Result<YandexSearchResponse> = runCatching {
        val response = api.search(text, type, page)
        if (response.isSuccessful) {
            response.body() ?: throw IllegalStateException("Empty response body")
        } else {
            throw RuntimeException("Search failed: ${response.code()} ${response.message()}")
        }
    }

    suspend fun searchTracks(text: String, page: Int = 0): Result<List<YandexTrack>> =
        search(text, "track", page).map { resp ->
            resp.result?.tracks?.results ?: emptyList()
        }

    suspend fun searchAlbums(text: String, page: Int = 0): Result<List<YandexAlbum>> =
        search(text, "album", page).map { resp ->
            resp.result?.albums?.results ?: emptyList()
        }

    suspend fun searchArtists(text: String, page: Int = 0): Result<List<YandexArtist>> =
        search(text, "artist", page).map { resp ->
            resp.result?.artists?.results ?: emptyList()
        }

    // ── Artist / Album ────────────────────────────────────────────────────────

    suspend fun getArtist(artistId: String): Result<YandexArtist> = runCatching {
        val response = api.getArtist(artistId)
        if (response.isSuccessful) {
            response.body()?.result ?: throw IllegalStateException("Artist not found")
        } else {
            throw RuntimeException("Get artist failed: ${response.code()} ${response.message()}")
        }
    }

    suspend fun getAlbumWithTracks(albumId: String): Result<YandexAlbum> = runCatching {
        val response = api.getAlbumWithTracks(albumId)
        if (response.isSuccessful) {
            response.body()?.result ?: throw IllegalStateException("Album not found")
        } else {
            throw RuntimeException("Get album failed: ${response.code()} ${response.message()}")
        }
    }

    // ── Streaming URL resolution ──────────────────────────────────────────────

    /**
     * Resolve a direct streaming URL for a Yandex Music track.
     *
     * The 3-step process:
     * 1. Fetch download info variants from the API.
     * 2. Fetch the XML at the chosen `downloadInfoUrl` to get host/path/ts/s.
     * 3. Sign with `MD5("XGRlBW9FXlekgbPrRHuCG" + path.drop(1) + s)` and build the URL.
     *
     * @param trackId  The Yandex track ID.
     * @param codec    Preferred codec, e.g. `"mp3"` or `"flac"`. Falls back to first available.
     * @return The signed direct streaming URL, or a failed [Result].
     */
    suspend fun getTrackPlayUrl(
        trackId: String,
        codec: String = "mp3"
    ): Result<String> = runCatching {
        if (!isLoggedIn) {
            throw IllegalStateException("Not authenticated with Yandex Music")
        }
        
        // Step 1: Get download info variants
        val cleanTrackId = trackId.substringBefore(":")
        val downloadInfoResponse = api.getTrackDownloadInfo(cleanTrackId)
        if (!downloadInfoResponse.isSuccessful) {
            throw RuntimeException("Download info failed: ${downloadInfoResponse.code()} ${downloadInfoResponse.message()}")
        }
        val downloadInfos = downloadInfoResponse.body()?.result
            ?: throw IllegalStateException("Empty download info list")

        // Pick preferred codec or fall back to first
        val info = downloadInfos.firstOrNull { it.codec == codec }
            ?: downloadInfos.firstOrNull()
            ?: throw IllegalStateException("No download info available for track $trackId")

        val downloadInfoUrl = info.downloadInfoUrl
        val actualCodec = info.codec

        // Step 2: Fetch the XML from downloadInfoUrl
        val xmlBody = fetchDownloadInfoXml(downloadInfoUrl)

        // Step 3: Parse XML and build signed URL
        val host = xmlTag(xmlBody, "host")
        val path = xmlTag(xmlBody, "path")
        val ts = xmlTag(xmlBody, "ts")
        val s = xmlTag(xmlBody, "s")

        // Sign: MD5("XGRlBW9FXlekgbPrRHuSiA" + path without leading "/" + s)
        val signInput = "XGRlBW9FXlekgbPrRHuSiA" + path.substring(1) + s
        val hash = md5Hex(signInput)

        // The CDN endpoint is historically always /get-mp3/ even if the actual codec is aac
        "https://$host/get-mp3/$hash/$ts$path"
    }.onFailure {
        timber.log.Timber.e(it, "YANDEX_STREAM: getTrackPlayUrl failed for track $trackId")
    }

    /**
     * Resolve the best-quality streaming URL for a track (prefers FLAC, falls back to mp3).
     */
    suspend fun getBestTrackPlayUrl(trackId: String): Result<String> =
        // Force MP3 codec as FLAC/AAC CDN paths are currently unstable in the reverse-engineered API
        getTrackPlayUrl(trackId, "mp3")

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun fetchDownloadInfoXml(url: String): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Yandex-Music-API")
            .header("X-Yandex-Music-Client", "YandexMusicAndroid/24023621")
        
        tokenStore.accessToken?.let { token ->
            requestBuilder.header("Authorization", "OAuth $token")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch download info XML: ${response.code} for URL: $url")
            }
            return response.body?.string()
                ?: throw IllegalStateException("Empty XML response body")
        }
    }

    /**
     * Extract the text content of an XML tag. Simple regex — good enough for
     * the fixed structure Yandex returns.
     * ponytail: naive regex parser; upgrade to XmlPullParser if the XML ever varies.
     */
    private fun xmlTag(xml: String, tag: String): String {
        val regex = Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)
            ?: throw IllegalStateException("XML tag <$tag> not found")
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
