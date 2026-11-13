package com.cahyo.batur.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.AppContainer
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.TaskStats
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Checking : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    val baseUrl: StateFlow<String> = container.settings.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.DEFAULT_BASE_URL)

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _stats = MutableStateFlow<UiState<TaskStats>>(UiState.Loading)
    val stats: StateFlow<UiState<TaskStats>> = _stats.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    init {
        viewModelScope.launch { _deviceId.value = container.settings.deviceId() }
    }

    fun refresh() {
        viewModelScope.launch {
            if (_stats.value !is UiState.Success) _stats.value = UiState.Loading
            _stats.value = container.taskRepository.getStats().toState()
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _connection.value = ConnectionState.Checking
            _connection.value = container.checkHealth().fold(
                onSuccess = { ConnectionState.Connected },
                onFailure = { ConnectionState.Failed(it.message ?: "Gagal terhubung") },
            )
        }
    }

    fun saveBaseUrl(url: String) {
        viewModelScope.launch {
            container.settings.setBaseUrl(url)
            checkConnection()
            _stats.value = UiState.Loading
            refresh()
        }
    }
}
