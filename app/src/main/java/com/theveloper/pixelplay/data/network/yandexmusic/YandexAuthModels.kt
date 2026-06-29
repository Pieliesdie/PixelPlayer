package com.theveloper.pixelplay.data.network.yandexmusic

import com.google.gson.annotations.SerializedName

/**
 * Response from POST https://oauth.yandex.ru/device/code
 *
 * Returns the device_code, user_code, verification_url, interval, and expires_in.
 */
data class YandexDeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String = "",
    @SerializedName("user_code") val userCode: String = "",
    @SerializedName("verification_url") val verificationUrl: String = "",
    @SerializedName("interval") val interval: Int = 5,
    @SerializedName("expires_in") val expiresIn: Int = 600
)

/**
 * Response from POST https://oauth.yandex.ru/token
 *
 * On success contains access_token (and optionally refresh_token, etc.).
 * On pending/error contains error (e.g. "authorization_pending").
 */
data class YandexTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null
) {
    val isSuccess: Boolean get() = !accessToken.isNullOrBlank()
    val isPending: Boolean get() = error == "authorization_pending"
}
