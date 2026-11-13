package com.cahyo.batur.ui.models

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.AppContainer
import com.cahyo.batur.agent.LocalModelState
import com.cahyo.batur.agent.ModelRequirements
import com.cahyo.batur.data.prefs.ActiveModel
import com.cahyo.batur.data.remote.ModelDetail
import com.cahyo.batur.data.remote.ModelSummary
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toListState
import com.cahyo.batur.ui.common.toState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Model on-device yang filenya (pernah) ada di ponsel, ditampilkan saat server tidak terjangkau. */
data class LocalModelEntry(val model: ActiveModel, val state: LocalModelState)

class ModelsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ModelSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ModelSummary>>> = _state.asStateFlow()

    /** Status file per slug model on-device. */
    private val _local = MutableStateFlow<Map<String, LocalModelState>>(emptyMap())
    val local: StateFlow<Map<String, LocalModelState>> = _local.asStateFlow()

    /** Model di ponsel yang tetap bisa dikelola walau daftar dari server gagal dimuat. */
    private val _offline = MutableStateFlow<List<LocalModelEntry>>(emptyList())
    val offline: StateFlow<List<LocalModelEntry>> = _offline.asStateFlow()

    val activeSlug: StateFlow<String?> = container.settings.activeModel.map { it?.slug }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = container.modelRepository.getModels().toListState()
            refreshLocal()
        }
    }

    /** Dipanggil saat layar tampil lagi, misalnya setelah kembali dari Detail Model. */
    fun refreshLocal() {
        viewModelScope.launch { refreshLocalNow() }
    }

    private suspend fun refreshLocalNow() {
        val store = container.modelStore
        val models = (state.value as? UiState.Success)?.data.orEmpty()
        val active = container.settings.activeModel.first()
        withContext(Dispatchers.IO) {
            _local.value = models
                .filter { it.fileName != null }
                .associate { it.slug to store.currentStateOf(it.fileName!!, it.sizeBytes) }
            // Model aktif selalu ikut, walau datanya belum pernah tercatat (misalnya dari versi lama).
            val known = (store.knownModels() + listOfNotNull(active)).distinctBy { it.slug }
            _offline.value = known
                .map { LocalModelEntry(it, store.currentStateOf(it.fileName, it.sizeBytes)) }
                .filter { it.state !is LocalModelState.NotDownloaded }
        }
    }
}

/** Data minimal untuk bagian "Di ponsel ini": dari server, atau dari data lokal saat offline. */
data class LocalTarget(
    val model: ActiveModel,
    val downloadUrl: String?,
    val sizeLabel: String?,
    val ramLabel: String?,
)

/** RAM ponsel dibanding kebutuhan model. */
data class RamCheck(val totalBytes: Long, val requiredGb: Int) {
    val enough: Boolean get() = ModelRequirements.meetsRam(totalBytes, requiredGb)
    val warning: String
        get() = "RAM ponsel ini ${ModelRequirements.formatRam(totalBytes)}, sedangkan model ini butuh minimal " +
            "$requiredGb GB. Model bisa sangat lambat atau gagal dimuat."
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val container: AppContainer,
    appContext: Context,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])
    private val settings = container.settings
    private val totalRam = ModelRequirements.totalRamBytes(appContext)

    private val _state = MutableStateFlow<UiState<ModelDetail>>(UiState.Loading)
    val state: StateFlow<UiState<ModelDetail>> = _state.asStateFlow()

    /** Model dari server, atau data lokal model ini saat server tidak terjangkau. Null untuk model cloud. */
    val target: StateFlow<LocalTarget?> = combine(_state, settings.activeModel) { s, active ->
        when (s) {
            is UiState.Success -> s.data.toTarget()
            is UiState.Error -> withContext(Dispatchers.IO) {
                (container.modelStore.knownModels() + listOfNotNull(active)).firstOrNull { it.slug == slug }
            }?.let { LocalTarget(it, downloadUrl = null, sizeLabel = null, ramLabel = null) }
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Status file model di ponsel; null untuk model cloud. */
    val localState: StateFlow<LocalModelState?> = target
        .flatMapLatest { t ->
            if (t == null) flowOf(null) else container.modelStore.observe(t.model.fileName, t.model.sizeBytes)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isActive: StateFlow<Boolean> = settings.activeModel.map { it?.slug == slug }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = container.modelRepository.getModel(slug).toState()
        }
    }

    fun freeBytes(): Long = container.modelStore.freeBytes()

    /** Null kalau model tidak dikenal atau RAM tidak bisa dibaca. */
    fun ramCheck(): RamCheck? {
        val t = target.value ?: return null
        val required = ModelRequirements.requiredRamGb(t.model.slug, t.model.sizeBytes) ?: return null
        return RamCheck(totalRam, required).takeIf { totalRam > 0 }
    }

    fun download() {
        val t = target.value ?: return
        val url = t.downloadUrl ?: return
        _message.value = container.startModelDownload(t.model, url)
    }

    fun dismissFailure() {
        val t = target.value ?: return
        container.modelStore.dismissFailure(t.model.fileName)
    }

    /** Batalkan unduhan atau hapus file. Model dilepas dari memori dulu supaya file tidak sedang dipakai. */
    fun delete() {
        val t = target.value ?: return
        viewModelScope.launch {
            container.deleteModel(t.model)
            _message.value = "File ${t.model.name} dihapus dari ponsel."
        }
    }

    fun activate() {
        val t = target.value ?: return
        viewModelScope.launch {
            container.onDeviceEngine.release()
            settings.setActiveModel(t.model)
            container.agentOrchestrator.warmUp()
        }
    }

    fun deactivate() {
        viewModelScope.launch {
            container.onDeviceEngine.release()
            settings.setActiveModel(null)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun ModelDetail.toTarget(): LocalTarget? {
        val file = fileName ?: return null
        if (type != "on_device") return null
        return LocalTarget(ActiveModel(slug, name, file, sizeBytes), downloadUrl, sizeLabel, ramLabel)
    }
}
