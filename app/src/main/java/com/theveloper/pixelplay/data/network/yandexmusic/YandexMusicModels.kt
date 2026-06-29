package com.theveloper.pixelplay.data.network.yandexmusic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) element.content else element.toString()
        } else {
            decoder.decodeString()
        }
    }
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

// ── API response wrappers ────────────────────────────────────────────────────
// Every Yandex Music endpoint returns { invocationInfo, result }

@Serializable
data class YandexInvocationInfo(
    @SerialName("exec-duration-millis") val execDurationMillis: Double = 0.0,
    @SerialName("hostname") val hostname: String = "",
    @SerialName("req-id") val reqId: String = "",
    @SerialName("app-name") val appName: String? = null
)

@Serializable
data class YandexSearchResponse(
    @SerialName("invocationInfo") val invocationInfo: YandexInvocationInfo = YandexInvocationInfo(),
    @SerialName("result") val result: YandexSearchResult? = null
)

@Serializable
data class YandexDownloadInfoResponse(
    @SerialName("invocationInfo") val invocationInfo: YandexInvocationInfo = YandexInvocationInfo(),
    @SerialName("result") val result: List<YandexTrackDownloadInfo> = emptyList()
)

@Serializable
data class YandexArtistResponse(
    @SerialName("invocationInfo") val invocationInfo: YandexInvocationInfo = YandexInvocationInfo(),
    @SerialName("result") val result: YandexArtist? = null
)

@Serializable
data class YandexAlbumResponse(
    @SerialName("invocationInfo") val invocationInfo: YandexInvocationInfo = YandexInvocationInfo(),
    @SerialName("result") val result: YandexAlbum? = null
)

@Serializable
data class YandexAccountStatusResponse(
    @SerialName("invocationInfo") val invocationInfo: YandexInvocationInfo = YandexInvocationInfo(),
    @SerialName("result") val result: YandexAccountStatusResult? = null
)

@Serializable
data class YandexAccountStatusResult(
    @SerialName("account") val account: YandexAccountStatus? = null
)

@Serializable
data class YandexPlaylistsResponse(
    @SerialName("invocationInfo") val invocationInfo: YandexInvocationInfo = YandexInvocationInfo(),
    @SerialName("result") val result: List<YandexPlaylist> = emptyList()
)

@Serializable
data class YandexPlaylistResponse(
    @SerialName("invocationInfo") val invocationInfo: YandexInvocationInfo = YandexInvocationInfo(),
    @SerialName("result") val result: YandexPlaylist? = null
)

// ── Search ───────────────────────────────────────────────────────────────────

@Serializable
data class YandexSearchResult(
    @SerialName("searchRequestId") val searchRequestId: String = "",
    @SerialName("text") val text: String = "",
    @SerialName("misspellCorrected") val misspellCorrected: Boolean = false,
    @SerialName("nocorrect") val nocorrect: Boolean = false,
    @SerialName("tracks") val tracks: YandexSearchResultListTracks? = null,
    @SerialName("albums") val albums: YandexSearchResultListAlbums? = null,
    @SerialName("artists") val artists: YandexSearchResultListArtists? = null
)

@Serializable
data class YandexSearchResultListTracks(
    @SerialName("type") val type: String = "track",
    @SerialName("total") val total: Int = 0,
    @SerialName("perPage") val perPage: Int = 0,
    @SerialName("order") val order: Int = 0,
    @SerialName("results") val results: List<YandexTrack> = emptyList()
)

@Serializable
data class YandexSearchResultListAlbums(
    @SerialName("type") val type: String = "album",
    @SerialName("total") val total: Int = 0,
    @SerialName("perPage") val perPage: Int = 0,
    @SerialName("order") val order: Int = 0,
    @SerialName("results") val results: List<YandexAlbum> = emptyList()
)

@Serializable
data class YandexSearchResultListArtists(
    @SerialName("type") val type: String = "artist",
    @SerialName("total") val total: Int = 0,
    @SerialName("perPage") val perPage: Int = 0,
    @SerialName("order") val order: Int = 0,
    @SerialName("results") val results: List<YandexArtist> = emptyList()
)

// ── Track ────────────────────────────────────────────────────────────────────

@Serializable
data class YandexTrack(
    @Serializable(with = FlexibleStringSerializer::class) @SerialName("id") val id: String = "",
    @Serializable(with = FlexibleStringSerializer::class) @SerialName("realId") val realId: String? = null,
    @SerialName("title") val title: String = "",
    @SerialName("durationMs") val durationMs: Long = 0,
    @SerialName("fileSize") val fileSize: Long = 0,
    @SerialName("coverUri") val coverUri: String = "",
    @SerialName("ogImage") val ogImage: String = "",
    @SerialName("lyricsAvailable") val lyricsAvailable: Boolean = false,
    @SerialName("available") val available: Boolean = true,
    @SerialName("availableForPremiumUsers") val availableForPremiumUsers: Boolean = false,
    @SerialName("artists") val artists: List<YandexArtist> = emptyList(),
    @SerialName("albums") val albums: List<YandexAlbum> = emptyList()
)

// ── Artist ───────────────────────────────────────────────────────────────────

@Serializable
data class YandexArtist(
    @Serializable(with = FlexibleStringSerializer::class) @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("cover") val cover: YandexCover? = null,
    @SerialName("genres") val genres: List<String> = emptyList(),
    @SerialName("popularTracks") val popularTracks: List<YandexTrack>? = null,
    @SerialName("ticketsAvailable") val ticketsAvailable: Boolean? = null
)

// ── Album ────────────────────────────────────────────────────────────────────

@Serializable
data class YandexAlbum(
    @SerialName("id") val id: Long = 0,
    @SerialName("title") val title: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("metaType") val metaType: String = "",
    @SerialName("year") val year: Int = 0,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("coverUri") val coverUri: String = "",
    @SerialName("ogImage") val ogImage: String = "",
    @SerialName("genre") val genre: String = "",
    @SerialName("trackCount") val trackCount: Int = 0,
    @SerialName("artists") val artists: List<YandexArtist> = emptyList(),
    @SerialName("labels") val labels: List<YandexLabel> = emptyList(),
    @SerialName("available") val available: Boolean = true,
    @SerialName("volumes") val volumes: List<List<YandexTrack>>? = null
)

@Serializable
data class YandexLabel(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String = ""
)

// ── Download info ────────────────────────────────────────────────────────────

@Serializable
data class YandexTrackDownloadInfo(
    @SerialName("codec") val codec: String = "",
    @SerialName("gain") val gain: Boolean = false,
    @SerialName("preview") val preview: Boolean = false,
    @SerialName("downloadInfoUrl") val downloadInfoUrl: String = "",
    @SerialName("direct") val direct: Boolean = false,
    @SerialName("bitrateInKbps") val bitrateInKbps: Int = 0
)

// ── Cover ────────────────────────────────────────────────────────────────────

@Serializable
data class YandexCover(
    @SerialName("type") val type: String = "",
    @SerialName("uri") val uri: String? = null,
    @SerialName("dir") val dir: String? = null,
    @SerialName("itemsUri") val itemsUri: List<String>? = null,
    @SerialName("custom") val custom: Boolean = false
)

// ── Account ──────────────────────────────────────────────────────────────────

@Serializable
data class YandexAccountStatus(
    @SerialName("uid") val uid: Long = 0,
    @SerialName("login") val login: String = "",
    @SerialName("fullName") val fullName: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("avatarHash") val avatarHash: String = "",
    @SerialName("region") val region: Int = 0
) {
    val nickname: String
        get() = displayName.ifBlank { fullName.ifBlank { login } }
    val avatarUrl: String?
        get() = if (avatarHash.isNotBlank()) "https://avatars.mds.yandex.net/get-yapic/$avatarHash/islands-200" else null
}

// ── Playlist ────────────────────────────────────────────────────────────────

@Serializable
data class YandexPlaylist(
    @SerialName("kind") val kind: Long = 0,
    @SerialName("title") val title: String = "",
    @SerialName("trackCount") val trackCount: Int = 0,
    @SerialName("cover") val cover: YandexPlaylistCover? = null,
    @SerialName("owner") val owner: YandexPlaylistOwner? = null,
    @SerialName("tracks") val tracks: List<YandexPlaylistTrackEntry> = emptyList()
)

@Serializable
data class YandexPlaylistCover(
    @SerialName("type") val type: String = "",
    @SerialName("uri") val uri: String = "",
    @SerialName("custom") val custom: Boolean = false
)

@Serializable
data class YandexPlaylistOwner(
    @SerialName("uid") val uid: Long = 0,
    @SerialName("login") val login: String = "",
    @SerialName("name") val name: String = ""
)

@Serializable
data class YandexPlaylistTrackEntry(
    @Serializable(with = FlexibleStringSerializer::class) @SerialName("id") val id: String = "",
    @SerialName("track") val track: YandexTrack? = null
)

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Build a full HTTPS image URL from a Yandex `coverUri` / `ogImage` template. */
fun yandexCoverUrl(uri: String, size: String = "300x300"): String =
    "https://${uri.replace("%%", size)}"
