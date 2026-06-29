package com.theveloper.pixelplay.data.network.yandexmusic

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Retrofit service for Yandex OAuth device flow.
 *
 * Base URL: `https://oauth.yandex.ru/`
 */
interface YandexAuthApiService {

    /**
     * Request a device code for the device flow.
     *
     * POST https://oauth.yandex.ru/device/code
     */
    @FormUrlEncoded
    @POST("device/code")
    fun requestDeviceCode(
        @Field("client_id") clientId: String = YANDEX_CLIENT_ID,
        @Field("grant_type") grantType: String = "device_code"
    ): Call<YandexDeviceCodeResponse>

    /**
     * Poll for the access token using the device code.
     *
     * POST https://oauth.yandex.ru/token
     */
    @FormUrlEncoded
    @POST("token")
    fun pollForToken(
        @Field("grant_type") grantType: String = "device_code",
        @Field("code") deviceCode: String,
        @Field("client_id") clientId: String = YANDEX_CLIENT_ID,
        @Field("client_secret") clientSecret: String = YANDEX_CLIENT_SECRET
    ): Call<YandexTokenResponse>

    companion object {
        const val YANDEX_CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
        const val YANDEX_CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300"
    }
}
