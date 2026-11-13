package com.cahyo.batur.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.data.remote.Note
import com.cahyo.batur.data.repository.NoteRepository
import com.cahyo.batur.data.repository.PendingNote
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Note>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Note>>> = _state.asStateFlow()

    /** Catatan yang masih di antrean ponsel. */
    val pending: StateFlow<List<PendingNote>> = repository.pending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = _state.value is UiState.Success
            _state.value = repository.getNotes().toListState()
            _isRefreshing.value = false
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch {
            repository.delete(note.id)
                .onSuccess {
                    _state.update { s ->
                        val rest = (s as? UiState.Success)?.data?.filterNot { it.id == note.id } ?: return@update s
                        if (rest.isEmpty()) UiState.Empty else UiState.Success(rest)
                    }
                    _message.value = "Catatan dihapus"
                }
                .onFailure { _message.value = it.message ?: "Catatan gagal dihapus" }
        }
    }

    fun deletePending(note: PendingNote) {
        viewModelScope.launch {
            repository.deletePending(note.clientId)
            _message.value = "Catatan dihapus dari antrean"
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
