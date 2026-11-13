package com.cahyo.batur

import android.app.Application
import android.content.Context
import com.cahyo.batur.agent.AgentOrchestrator
import com.cahyo.batur.agent.CloudAgentEngine
import com.cahyo.batur.agent.LocalModelState
import com.cahyo.batur.agent.ModelStore
import com.cahyo.batur.agent.OnDeviceAgentEngine
import com.cahyo.batur.data.prefs.ActiveModel
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiClient
import com.cahyo.batur.data.remote.AppVersion
import com.cahyo.batur.data.remote.HealthStatus
import com.cahyo.batur.data.remote.apiCall
import com.cahyo.batur.data.repository.DeviceRepository
import com.cahyo.batur.data.repository.ModelRepository
import com.cahyo.batur.data.repository.NoteRepository
import com.cahyo.batur.data.repository.SkillRepository
import com.cahyo.batur.data.repository.TaskRepository
import com.cahyo.batur.speech.SpeechPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BaturApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onAppStart()
    }
}

/** Dependensi yang dipakai bersama oleh seluruh layar. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Untuk pekerjaan yang harus tetap jalan walau layar ditutup (sinkron antrean, aktivasi model). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(appContext)
    private val apiClient = ApiClient(settings)

    val deviceRepository = DeviceRepository(apiClient, settings)
    val skillRepository = SkillRepository(deviceRepository)
    val noteRepository = NoteRepository(deviceRepository, settings)
    val taskRepository = TaskRepository(deviceRepository, settings, noteRepository)
    val modelRepository = ModelRepository(apiClient)

    val modelStore = ModelStore(appContext)
    val onDeviceEngine = OnDeviceAgentEngine(appContext, settings, modelStore, skillRepository)

    val agentOrchestrator = AgentOrchestrator(
        appContext = appContext,
        cloudEngine = CloudAgentEngine(deviceRepository),
        onDeviceEngine = onDeviceEngine,
        skillRepository = skillRepository,
        taskRepository = taskRepository,
        noteRepository = noteRepository,
        settings = settings,
    )

    /** Membacakan jawaban Batur (suara cloud, cadangan TTS bawaan Android). */
    val speech = SpeechPlayer(appContext, deviceRepository, settings)

    /** Teks yang diisikan ke kolom perintah Agent, misalnya dari contoh di Detail Skill. */
    val agentDraft = MutableStateFlow<String?>(null)

    private val _update = MutableStateFlow<AppVersion?>(null)

    /** Versi baru di server (dari GET /api/app/version), null kalau tidak ada atau sudah ditutup. */
    val update: StateFlow<AppVersion?> = _update.asStateFlow()

    fun onAppStart() {
        appScope.launch { taskRepository.syncPending() }
        appScope.launch { checkForUpdate() }
        modelStore.autoActivateCandidates().forEach(::watchDownload)
    }

    suspend fun checkHealth(baseUrl: String? = null): Result<HealthStatus> {
        if (baseUrl == null) return apiCall { apiClient.api().health() }
        val api = runCatching { apiClient.apiFor(SettingsStore.normalizeBaseUrl(baseUrl)) }
            .getOrElse { return Result.failure(IllegalArgumentException("Alamat server tidak valid.")) }
        return apiCall { api.health() }
    }

    private suspend fun checkForUpdate() {
        apiCall { apiClient.api().appVersion() }.onSuccess { latest ->
            if (latest.versionCode > BuildConfig.VERSION_CODE) _update.value = latest
        }
    }

    fun dismissUpdate() {
        _update.value = null
    }

    /**
     * Mulai unduh model dan pantau dari level aplikasi: model pertama yang selesai diunduh
     * langsung diaktifkan walau layar Detail Model sudah ditutup.
     * @return pesan kesalahan, atau null kalau unduhan berhasil dimulai.
     */
    fun startModelDownload(model: ActiveModel, url: String): String? {
        val error = modelStore.startDownload(url, model.fileName, "Model ${model.name}", model.sizeBytes)
        if (error == null) {
            modelStore.remember(model, autoActivate = true)
            watchDownload(model)
        }
        return error
    }

    private val watching = mutableSetOf<String>()

    private fun watchDownload(model: ActiveModel) {
        synchronized(watching) { if (!watching.add(model.slug)) return }
        appScope.launch {
            try {
                val done = modelStore.observe(model.fileName, model.sizeBytes).first { it !is LocalModelState.Downloading }
                if (done is LocalModelState.Ready && settings.activeModel.first() == null) {
                    settings.setActiveModel(model)
                    agentOrchestrator.warmUp()
                }
                modelStore.clearAutoActivate(model.slug)
            } finally {
                synchronized(watching) { watching.remove(model.slug) }
            }
        }
    }

    /** Hapus file model; kalau sedang aktif, lepas dari memori dan nonaktifkan dulu. */
    suspend fun deleteModel(model: ActiveModel) {
        if (settings.activeModel.first()?.slug == model.slug) {
            onDeviceEngine.release()
            settings.setActiveModel(null)
        }
        withContext(Dispatchers.IO) {
            modelStore.delete(model.fileName)
            modelStore.forget(model.slug)
        }
    }
}
