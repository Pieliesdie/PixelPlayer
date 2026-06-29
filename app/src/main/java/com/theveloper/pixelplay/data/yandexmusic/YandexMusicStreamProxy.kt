package com.theveloper.pixelplay.data.yandexmusic

import com.theveloper.pixelplay.data.network.yandexmusic.YandexMusicManager
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YandexMusicStreamProxy @Inject constructor(
    private val yandexMusicManager: YandexMusicManager,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes = setOf("yandex.net", "yandex.ru")
    override val cacheExpirationMs = 15L * 60 * 1000
    override val proxyTag = "YandexMusicStreamProxy"
    override val routePath = "/yandexmusic/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = "yandexmusic"
    override val routePrefix = "/yandexmusic"

    override fun parseRouteParam(value: String): String = value

    override fun validateId(id: String): Boolean = id.isNotBlank()

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? =
        yandexMusicManager.getBestTrackPlayUrl(id).getOrNull()

    fun resolveYandexMusicUri(uriString: String): String? = resolveUri(uriString)
}
