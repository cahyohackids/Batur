package com.cahyo.batur

import android.app.Application
import android.content.Context
import com.cahyo.batur.agent.AgentOrchestrator
import com.cahyo.batur.agent.CloudAgentEngine
import com.cahyo.batur.agent.ModelStore
import com.cahyo.batur.agent.OnDeviceAgentEngine
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiClient
import com.cahyo.batur.data.remote.HealthStatus
import com.cahyo.batur.data.remote.apiCall
import com.cahyo.batur.data.repository.ModelRepository
import com.cahyo.batur.data.repository.NoteRepository
import com.cahyo.batur.data.repository.SkillRepository
import com.cahyo.batur.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow

class BaturApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Dependensi yang dipakai bersama oleh seluruh layar. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val apiClient = ApiClient(settings)

    val skillRepository = SkillRepository(apiClient)
    val taskRepository = TaskRepository(apiClient, settings)
    val modelRepository = ModelRepository(apiClient)
    val noteRepository = NoteRepository(apiClient, settings)

    val modelStore = ModelStore(context)
    val onDeviceEngine = OnDeviceAgentEngine(context.applicationContext, settings, modelStore, skillRepository)

    val agentOrchestrator = AgentOrchestrator(
        appContext = context.applicationContext,
        cloudEngine = CloudAgentEngine(apiClient, settings),
        onDeviceEngine = onDeviceEngine,
        skillRepository = skillRepository,
        taskRepository = taskRepository,
        noteRepository = noteRepository,
        settings = settings,
    )

    /** Teks yang diisikan ke kolom perintah Agent, misalnya dari contoh di Detail Skill. */
    val agentDraft = MutableStateFlow<String?>(null)

    suspend fun checkHealth(): Result<HealthStatus> = apiCall { apiClient.api().health() }
}
