package com.cahyo.batur.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.data.remote.SkillSummary
import com.cahyo.batur.data.repository.SkillRepository
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SkillsViewModel(private val repository: SkillRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<SkillSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<SkillSummary>>> = _state.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun selectCategory(category: String?) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
        _state.value = UiState.Loading
        refresh()
    }

    /** Dipanggil setiap halaman tampil, supaya status aktif hasil toggle di Detail ikut terbarui. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = _state.value is UiState.Success
            val category = _selectedCategory.value
            val result = repository.getSkills(category)
            if (category == null) {
                result.onSuccess { list -> _categories.value = list.map { it.category }.distinct() }
            }
            _state.value = result.toListState()
            _isRefreshing.value = false
        }
    }
}
