package com.cahyo.batur.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.agent.LocalModelState
import com.cahyo.batur.agent.ModelStore
import com.cahyo.batur.agent.OnDeviceAgentEngine
import com.cahyo.batur.data.prefs.ActiveModel
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ModelDetail
import com.cahyo.batur.data.remote.ModelSummary
import com.cahyo.batur.data.repository.ModelRepository
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toListState
import com.cahyo.batur.ui.common.toState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelsViewModel(
    private val repository: ModelRepository,
    private val modelStore: ModelStore,
    settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ModelSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ModelSummary>>> = _state.asStateFlow()

    /** Slug model on-device yang filenya sudah lengkap di ponsel ini. */
    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()

    val activeSlug: StateFlow<String?> = settings.activeModel.map { it?.slug }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = repository.getModels()
            _state.value = result.toListState()
            refreshDownloaded(result.getOrNull().orEmpty())
        }
    }

    /** Dipanggil saat layar tampil lagi, misalnya setelah kembali dari Detail Model. */
    fun refreshDownloaded() {
        (state.value as? UiState.Success)?.let { viewModelScope.launch { refreshDownloaded(it.data) } }
    }

    private suspend fun refreshDownloaded(models: List<ModelSummary>) {
        _downloaded.value = withContext(Dispatchers.IO) {
            models.filter { m -> m.fileName != null && modelStore.readyFile(m.fileName, m.sizeBytes) != null }
                .map { it.slug }
                .toSet()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ModelRepository,
    private val modelStore: ModelStore,
    private val settings: SettingsStore,
    private val onDeviceEngine: OnDeviceAgentEngine,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    private val _state = MutableStateFlow<UiState<ModelDetail>>(UiState.Loading)
    val state: StateFlow<UiState<ModelDetail>> = _state.asStateFlow()

    /** Status file model di ponsel; null untuk model cloud. */
    val localState: StateFlow<LocalModelState?> = _state
        .flatMapLatest { s ->
            val model = (s as? UiState.Success)?.data
            if (model?.fileName == null || model.downloadUrl == null) flowOf(null)
            else modelStore.observe(model.fileName, model.sizeBytes).onEach { local ->
                // Model pertama yang selesai diunduh langsung dipakai, supaya agent tidak perlu disetel manual.
                if (local is LocalModelState.Ready && settings.activeModel.first() == null) activate()
            }
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
            _state.value = repository.getModel(slug).toState()
        }
    }

    fun freeBytes(): Long = modelStore.freeBytes()

    fun download() {
        val model = currentModel() ?: return
        val url = model.downloadUrl ?: return
        val fileName = model.fileName ?: return
        _message.value = modelStore.startDownload(url, fileName, "Model ${model.name}", model.sizeBytes)
    }

    /** Batalkan unduhan atau hapus file. Model dilepas dari memori dulu supaya file tidak sedang dipakai. */
    fun delete() {
        val model = currentModel() ?: return
        val fileName = model.fileName ?: return
        viewModelScope.launch {
            if (isActive.value || settings.activeModel.first()?.slug == slug) {
                onDeviceEngine.release()
                settings.setActiveModel(null)
            }
            withContext(Dispatchers.IO) { modelStore.delete(fileName) }
            _message.value = "File ${model.name} dihapus dari ponsel."
        }
    }

    fun activate() {
        val model = currentModel() ?: return
        val fileName = model.fileName ?: return
        viewModelScope.launch {
            onDeviceEngine.release()
            settings.setActiveModel(ActiveModel(model.slug, model.name, fileName, model.sizeBytes))
        }
    }

    fun deactivate() {
        viewModelScope.launch {
            onDeviceEngine.release()
            settings.setActiveModel(null)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun currentModel(): ModelDetail? = (_state.value as? UiState.Success)?.data
}
