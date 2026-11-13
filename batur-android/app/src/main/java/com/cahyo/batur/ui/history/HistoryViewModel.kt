package com.cahyo.batur.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.data.remote.TaskSummary
import com.cahyo.batur.data.repository.TaskRepository
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<TaskSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<TaskSummary>>> = _state.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun selectStatus(status: String?) {
        if (_status.value == status) return
        _status.value = status
        _state.value = UiState.Loading
        refresh()
    }

    /** Dipanggil setiap halaman tampil, supaya tugas baru dan tugas yang dihapus langsung terlihat. */
    private var refreshJob: Job? = null

    fun refresh() {
        // Filter bisa diganti saat refresh lama masih jalan; hasil lama tidak boleh menimpa yang baru.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = _state.value is UiState.Success
            _state.value = repository.getTasks(_status.value).toListState()
            _isRefreshing.value = false
        }
    }

    companion object {
        val STATUS_FILTERS = listOf("success", "answered", "cancelled", "failed")
    }
}
