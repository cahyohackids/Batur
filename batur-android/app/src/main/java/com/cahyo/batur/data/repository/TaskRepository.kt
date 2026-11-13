package com.cahyo.batur.data.repository

import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiClient
import com.cahyo.batur.data.remote.ApiException
import com.cahyo.batur.data.remote.CreateTaskRequest
import com.cahyo.batur.data.remote.CreatedTask
import com.cahyo.batur.data.remote.DeleteResult
import com.cahyo.batur.data.remote.TaskDetail
import com.cahyo.batur.data.remote.TaskStats
import com.cahyo.batur.data.remote.TaskSummary
import com.cahyo.batur.data.remote.apiCall
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RecordOutcome {
    data class Saved(val id: Int) : RecordOutcome
    data object Queued : RecordOutcome
    data class Failed(val message: String) : RecordOutcome
}

class TaskRepository(
    private val client: ApiClient,
    private val settings: SettingsStore,
) {
    private val gson = Gson()
    private val queueMutex = Mutex()

    suspend fun getTasks(status: String? = null): Result<List<TaskSummary>> {
        syncPending()
        val deviceId = settings.deviceId()
        return apiCall { client.api().getTasks(deviceId = deviceId, status = status) }
    }

    suspend fun getStats(): Result<TaskStats> {
        val deviceId = settings.deviceId()
        return apiCall { client.api().getTaskStats(deviceId) }
    }

    suspend fun getTask(id: Int): Result<TaskDetail> =
        apiCall { client.api().getTask(id) }

    suspend fun createTask(request: CreateTaskRequest): Result<CreatedTask> =
        apiCall { client.api().createTask(request) }

    /**
     * Simpan jejak ke server. Kalau server tidak terjangkau (misalnya mode on-device tanpa internet),
     * jejak diantrekan di ponsel dan dikirim otomatis saat berhasil terhubung lagi.
     */
    suspend fun record(request: CreateTaskRequest): RecordOutcome {
        syncPending()
        return createTask(request).fold(
            onSuccess = { RecordOutcome.Saved(it.id) },
            onFailure = { e ->
                if ((e as? ApiException)?.offline == true) {
                    queueMutex.withLock { savePending(loadPending() + request) }
                    RecordOutcome.Queued
                } else {
                    RecordOutcome.Failed(e.message ?: "Riwayat gagal disimpan")
                }
            },
        )
    }

    /** Kirim jejak yang tertunda. @return jumlah yang berhasil terkirim. */
    suspend fun syncPending(): Int = queueMutex.withLock {
        val pending = loadPending()
        if (pending.isEmpty()) return@withLock 0
        val remaining = mutableListOf<CreateTaskRequest>()
        var sent = 0
        for ((index, request) in pending.withIndex()) {
            val result = createTask(request)
            if (result.isSuccess) {
                sent++
            } else if ((result.exceptionOrNull() as? ApiException)?.offline == true) {
                // Masih offline: simpan sisanya untuk dicoba lagi nanti.
                remaining += pending.drop(index)
                break
            }
            // Ditolak server (data tidak valid): dibuang supaya antrean tidak macet.
        }
        savePending(remaining)
        sent
    }

    private suspend fun loadPending(): List<CreateTaskRequest> {
        val json = settings.pendingTasks() ?: return emptyList()
        return runCatching {
            gson.fromJson<List<CreateTaskRequest>>(json, object : TypeToken<List<CreateTaskRequest>>() {}.type)
        }.getOrDefault(emptyList())
    }

    private suspend fun savePending(list: List<CreateTaskRequest>) {
        settings.setPendingTasks(if (list.isEmpty()) null else gson.toJson(list))
    }

    suspend fun deleteTask(id: Int): Result<DeleteResult> =
        apiCall { client.api().deleteTask(id) }
}
