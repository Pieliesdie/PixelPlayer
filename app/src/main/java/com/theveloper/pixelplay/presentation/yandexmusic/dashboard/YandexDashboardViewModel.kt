package com.theveloper.pixelplay.presentation.yandexmusic.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.YandexMusicPlaylistEntity
import com.theveloper.pixelplay.data.yandexmusic.YandexMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YandexDashboardViewModel @Inject constructor(
    private val repository: YandexMusicRepository
) : ViewModel() {

    val playlists: StateFlow<List<YandexMusicPlaylistEntity>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val userNickname: String? get() = repository.userNickname
    val userAvatar: String? get() = repository.userAvatarUrl

    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow

    init {
        syncPlaylists()
    }

    fun syncAllPlaylistsAndSongs() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing all playlists and songs..."
            val result = repository.syncAllPlaylistsAndSongs()
            result.fold(
                onSuccess = { summary ->
                    _syncMessage.value = if (summary.failedPlaylistCount == 0) {
                        "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs"
                    } else {
                        "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs (${summary.failedPlaylistCount} failed)"
                    }
                },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun syncPlaylists() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing playlists..."
            val result = repository.syncUserPlaylists()
            result.fold(
                onSuccess = { _syncMessage.value = "Synced ${it.size} playlists" },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun syncPlaylistSongs(playlistKind: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing songs..."
            val result = repository.syncPlaylistSongs(playlistKind)
            result.fold(
                onSuccess = { _syncMessage.value = "Synced $it songs" },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun deletePlaylist(playlistKind: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistKind)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
