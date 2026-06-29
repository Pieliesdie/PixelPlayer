package com.theveloper.pixelplay.data.network.yandexmusic

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure token storage for Yandex Music OAuth tokens.
 *
 * Uses EncryptedSharedPreferences (same pattern as NeteaseRepository, JellyfinRepository, etc.)
 * Falls back to plain SharedPreferences if the keystore is unavailable.
 */
@Singleton
class YandexTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "YandexTokenStore"
        const val PREFS_NAME = "yandex_music_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "$TAG: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    @Volatile
    var accessToken: String? = prefs.getString(KEY_ACCESS_TOKEN, null)
        private set

    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank()

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        accessToken = token
        Timber.d("$TAG: Token saved")
    }

    fun clearToken() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
        accessToken = null
        Timber.d("$TAG: Token cleared")
    }
}
