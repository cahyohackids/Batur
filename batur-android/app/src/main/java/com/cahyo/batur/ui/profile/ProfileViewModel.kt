package com.cahyo.batur.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.AppContainer
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.AppVersion
import com.cahyo.batur.data.remote.HealthStatus
import com.cahyo.batur.data.remote.TaskStats
import com.cahyo.batur.ui.common.ConnectionState
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toConnectionState
import com.cahyo.batur.ui.common.toState
import com.cahyo.batur.speech.SpeechState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    val baseUrl: StateFlow<String> = container.settings.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.DEFAULT_BASE_URL)

    /** Null sampai perangkat terdaftar (otomatis saat statistik pertama kali dimuat). */
    val deviceId: StateFlow<String?> = container.settings.deviceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val update: StateFlow<AppVersion?> = container.update

    val speechVoice: StateFlow<String> = container.settings.speechVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.VOICE_FEMALE)

    val autoSpeak: StateFlow<Boolean> = container.settings.autoSpeak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val speech: StateFlow<SpeechState> = container.speech.state

    fun setSpeechVoice(voice: String) {
        viewModelScope.launch {
            container.settings.setSpeechVoice(voice)
            previewVoice()
        }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { container.settings.setAutoSpeak(enabled) }
    }

    fun previewVoice() = container.speech.speak(PREVIEW_ID, PREVIEW_TEXT)

    fun stopSpeech() = container.speech.stop()

    override fun onCleared() = stopSpeech()

    private val _stats = MutableStateFlow<UiState<TaskStats>>(UiState.Loading)
    val stats: StateFlow<UiState<TaskStats>> = _stats.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            if (_stats.value !is UiState.Success) _stats.value = UiState.Loading
            _stats.value = container.taskRepository.getStats().toState()
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _connection.value = ConnectionState.Checking
            _connection.value = container.checkHealth().toConnectionState()
        }
    }

    suspend fun testUrl(url: String): Result<HealthStatus> = container.checkHealth(url)

    fun saveBaseUrl(url: String) {
        viewModelScope.launch {
            container.settings.setBaseUrl(url)
            checkConnection()
            _stats.value = UiState.Loading
            refresh()
        }
    }

    fun dismissUpdate() = container.dismissUpdate()

    private companion object {
        /** Id negatif supaya tidak bentrok dengan id pesan di layar Agent. */
        const val PREVIEW_ID = -1L
        const val PREVIEW_TEXT = "Halo, aku Batur. Siap bantu pasang alarm, bikin jadwal, atau simpan catatan."
    }
}
