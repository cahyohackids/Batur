package com.cahyo.batur.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.AppContainer
import com.cahyo.batur.agent.ModelRecommendation
import com.cahyo.batur.agent.ModelRequirements
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.HealthStatus
import com.cahyo.batur.ui.common.ConnectionState
import com.cahyo.batur.ui.common.toConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer, appContext: Context) : ViewModel() {

    val totalRamBytes: Long = ModelRequirements.totalRamBytes(appContext)
    val recommendation: ModelRecommendation = ModelRequirements.recommend(totalRamBytes)

    val baseUrl: StateFlow<String> = container.settings.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.DEFAULT_BASE_URL)

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private var checkJob: Job? = null

    fun checkConnection() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _connection.value = ConnectionState.Checking
            _connection.value = container.checkHealth().toConnectionState()
        }
    }

    suspend fun testUrl(url: String): Result<HealthStatus> = container.checkHealth(url)

    fun saveBaseUrl(url: String) {
        viewModelScope.launch {
            container.settings.setBaseUrl(url)
            checkConnection()
        }
    }

    /** Tandai onboarding selesai, lalu [then] (navigasi) dijalankan. */
    fun finish(then: () -> Unit) {
        viewModelScope.launch {
            container.settings.setOnboardingDone()
            then()
        }
    }
}
