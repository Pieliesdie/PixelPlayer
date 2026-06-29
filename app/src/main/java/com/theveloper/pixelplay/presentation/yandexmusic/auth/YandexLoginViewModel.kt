package com.theveloper.pixelplay.presentation.yandexmusic.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.network.yandexmusic.YandexAuthApiService
import com.theveloper.pixelplay.data.network.yandexmusic.YandexDeviceCodeResponse
import com.theveloper.pixelplay.data.network.yandexmusic.YandexTokenResponse
import com.theveloper.pixelplay.data.network.yandexmusic.YandexTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.await
import timber.log.Timber
import javax.inject.Inject

sealed class YandexLoginState {
    data object Idle : YandexLoginState()
    data object RequestingCode : YandexLoginState()
    data class WaitingForUser(
        val userCode: String,
        val verificationUrl: String,
        val expiresInSeconds: Int
    ) : YandexLoginState()
    data object Polling : YandexLoginState()
    data class Success(val token: String) : YandexLoginState()
    data class Error(val message: String) : YandexLoginState()
}

@HiltViewModel
class YandexLoginViewModel @Inject constructor(
    private val authApi: YandexAuthApiService,
    private val tokenStore: YandexTokenStore,
    private val manager: com.theveloper.pixelplay.data.network.yandexmusic.YandexMusicManager
) : ViewModel() {

    companion object {
        private const val TAG = "YandexLoginVM"
    }

    private val _state = MutableStateFlow<YandexLoginState>(YandexLoginState.Idle)
    val state: StateFlow<YandexLoginState> = _state.asStateFlow()

    fun startDeviceFlow() {
        if (_state.value !is YandexLoginState.Idle && _state.value !is YandexLoginState.Error) return

        _state.value = YandexLoginState.RequestingCode
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    authApi.requestDeviceCode().await()
                }
                if (response.deviceCode.isBlank()) {
                    _state.value = YandexLoginState.Error("Failed to get device code")
                    return@launch
                }
                _state.value = YandexLoginState.WaitingForUser(
                    userCode = response.userCode,
                    verificationUrl = response.verificationUrl,
                    expiresInSeconds = response.expiresIn
                )
                // Automatically start polling
                startPolling(response.deviceCode, response.interval)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to request device code")
                _state.value = YandexLoginState.Error(e.message ?: "Failed to request device code")
            }
        }
    }

    private fun startPolling(deviceCode: String, intervalSeconds: Int) {
        viewModelScope.launch {
            val pollIntervalMs = (intervalSeconds.coerceAtLeast(1)) * 1000L
            // ponytail: simple loop — the server tells us the interval; we trust it.
            while (_state.value is YandexLoginState.WaitingForUser || _state.value is YandexLoginState.Polling) {
                delay(pollIntervalMs)
                if (_state.value is YandexLoginState.Error || _state.value is YandexLoginState.Success) break

                _state.value = YandexLoginState.Polling
                try {
                    val response = withContext(Dispatchers.IO) {
                        authApi.pollForToken(deviceCode = deviceCode).await()
                    }
                    when {
                        response.isSuccess -> {
                            val token = response.accessToken!!
                            tokenStore.saveToken(token)
                            // Fetch account status to populate cached uid/nickname/avatar
                            manager.fetchAccountStatus()
                            _state.value = YandexLoginState.Success(token)
                            Timber.d("$TAG: Login successful")
                            return@launch
                        }
                        response.isPending -> {
                            // Keep polling — restore waiting state so UI stays correct
                            val current = _state.value
                            if (current is YandexLoginState.Polling) {
                                // stay in Polling; UI shows a spinner
                            }
                        }
                        else -> {
                            val errorMsg = response.errorDescription ?: response.error ?: "Authorization failed"
                            _state.value = YandexLoginState.Error(errorMsg)
                            Timber.w("$TAG: Token poll error: $errorMsg")
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Token poll request failed")
                    // Transient network error — keep polling, don't give up
                }
            }
        }
    }

    fun clearError() {
        if (_state.value is YandexLoginState.Error) {
            _state.value = YandexLoginState.Idle
        }
    }
}
