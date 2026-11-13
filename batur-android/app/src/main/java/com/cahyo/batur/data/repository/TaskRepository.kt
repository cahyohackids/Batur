package com.cahyo.batur.data.repository

import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiException
import com.cahyo.batur.data.remote.CreateTaskRequest
import com.cahyo.batur.data.remote.CreatedTask
import com.cahyo.batur.data.remote.DeleteResult
import com.cahyo.batur.data.remote.TaskDetail
import com.cahyo.batur.data.remote.TaskStats
import com.cahyo.batur.data.remote.TaskSummary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface RecordOutcome {
    data class Saved(val id: Int) : RecordOutcome
    data object Queued : RecordOutcome
    data class Failed(val message: String) : RecordOutcome
}

class TaskRepository(
    private val device: DeviceRepository,
    private val settings: SettingsStore,
    private val noteRepository: NoteRepository,
) {
    private val gson = Gson()
    private val queueMutex = Mutex()

    suspend fun getTasks(status: String? = null): Result<List<TaskSummary>> {
        syncPending()
        return device.authed { getTasks(status = status) }
    }

    suspend fun getStats(): Result<TaskStats> = device.authed { getTaskStats() }

    suspend fun getTask(id: Int): Result<TaskDetail> = device.authed { getTask(id) }

    private suspend fun createTask(request: CreateTaskRequest): Result<CreatedTask> =
        device.authed { createTask(request) }

    /**
     * Simpan jejak ke server. Kalau gagal karena alasan sementara (offline, server sibuk, dibatasi),
     * jejak diantrekan di ponsel dan dikirim otomatis nanti. client_id membuat kiriman ulang tidak dobel.
     */
    suspend fun record(request: CreateTaskRequest): RecordOutcome {
        syncPending()
        val withId = request.copy(clientId = request.clientId ?: UUID.randomUUID().toString())
        return createTask(withId).fold(
            onSuccess = { RecordOutcome.Saved(it.id) },
            onFailure = { e ->
                if ((e as? ApiException)?.retryable == true) {
                    queueMutex.withLock { savePending(loadPending() + withId) }
                    RecordOutcome.Queued
                } else {
                    RecordOutcome.Failed(e.message ?: "Riwayat gagal disimpan")
                }
            },
        )
    }

    /** Kirim jejak dan catatan yang tertunda. @return jumlah jejak yang berhasil terkirim. */
    suspend fun syncPending(): Int {
        noteRepository.syncPending()
        return queueMutex.withLock {
            val pending = loadPending()
            if (pending.isEmpty()) return@withLock 0
            val remaining = mutableListOf<CreateTaskRequest>()
            var sent = 0
            for ((index, request) in pending.withIndex()) {
                val result = createTask(request)
                if (result.isSuccess) {
                    sent++
                } else if ((result.exceptionOrNull() as? ApiException)?.retryable == true) {
                    // Masih offline atau server sedang sibuk: simpan sisanya untuk dicoba lagi nanti.
                    remaining += pending.drop(index)
                    break
                }
                // Ditolak server (4xx, data tidak valid): dibuang supaya antrean tidak macet.
            }
            savePending(remaining)
            sent
        }
    }

    private suspend fun loadPending(): List<CreateTaskRequest> {
        val json = settings.pendingTasks() ?: return emptyList()
        val list = runCatching {
            gson.fromJson<List<CreateTaskRequest>>(json, object : TypeToken<List<CreateTaskRequest>>() {}.type)
        }.getOrNull().orEmpty()
        // Antrean dari versi 1.0 belum punya client_id; id yang diberikan di sini ikut tersimpan lagi.
        return list.map { if (it.clientId == null) it.copy(clientId = UUID.randomUUID().toString()) else it }
    }

    private suspend fun savePending(list: List<CreateTaskRequest>) {
        settings.setPendingTasks(if (list.isEmpty()) null else gson.toJson(list))
    }

    suspend fun deleteTask(id: Int): Result<DeleteResult> = device.authed { deleteTask(id) }
}
