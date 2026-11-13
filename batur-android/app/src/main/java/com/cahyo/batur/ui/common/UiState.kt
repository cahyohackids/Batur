package com.cahyo.batur.ui.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun <T> Result<List<T>>.toListState(): UiState<List<T>> = fold(
    onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
    onFailure = { UiState.Error(it.message ?: "Terjadi kesalahan") },
)

fun <T> Result<T>.toState(): UiState<T> = fold(
    onSuccess = { UiState.Success(it) },
    onFailure = { UiState.Error(it.message ?: "Terjadi kesalahan") },
)
