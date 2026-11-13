package com.cahyo.batur.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.data.remote.TaskDetail
import com.cahyo.batur.data.repository.TaskRepository
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : ViewModel() {

    private val id: Int = checkNotNull(savedStateHandle["id"])

    private val _state = MutableStateFlow<UiState<TaskDetail>>(UiState.Loading)
    val state: StateFlow<UiState<TaskDetail>> = _state.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = repository.getTask(id).toState()
        }
    }

    fun delete() {
        if (_deleting.value) return
        viewModelScope.launch {
            _deleting.value = true
            repository.deleteTask(id)
                .onSuccess { _deleted.value = true }
                .onFailure { _message.value = it.message }
            _deleting.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
