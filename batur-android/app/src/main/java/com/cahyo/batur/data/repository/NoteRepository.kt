package com.cahyo.batur.data.repository

import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiException
import com.cahyo.batur.data.remote.CreateNoteRequest
import com.cahyo.batur.data.remote.DeleteResult
import com.cahyo.batur.data.remote.Note
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/** Catatan yang belum terkirim ke server. */
data class PendingNote(
    val clientId: String,
    val content: String,
    val taskId: Int?,
    /** ISO-8601, waktu catatan dibuat di ponsel. */
    val createdAt: String,
)

sealed interface NoteOutcome {
    data class Saved(val note: Note) : NoteOutcome
    data object Queued : NoteOutcome
    data class Failed(val message: String) : NoteOutcome
}

class NoteRepository(
    private val device: DeviceRepository,
    private val settings: SettingsStore,
) {
    private val gson = Gson()
    private val queueMutex = Mutex()

    val pending: Flow<List<PendingNote>> = settings.pendingNotes.map(::decode)

    suspend fun getNotes(): Result<List<Note>> {
        syncPending()
        return device.authed { getNotes() }
    }

    /** Simpan ke server; kalau server tidak terjangkau, antrekan di ponsel dan kirim nanti. */
    suspend fun save(content: String, taskId: Int? = null): NoteOutcome {
        syncPending()
        val note = PendingNote(UUID.randomUUID().toString(), content, taskId, Instant.now().toString())
        return send(note).fold(
            onSuccess = { NoteOutcome.Saved(it) },
            onFailure = { e ->
                if ((e as? ApiException)?.retryable == true) {
                    queueMutex.withLock { savePending(loadPending() + note) }
                    NoteOutcome.Queued
                } else {
                    NoteOutcome.Failed(e.message ?: "Catatan gagal disimpan")
                }
            },
        )
    }

    suspend fun delete(id: Int): Result<DeleteResult> = device.authed { deleteNote(id) }

    /** Hapus catatan yang masih di antrean (belum pernah sampai ke server). */
    suspend fun deletePending(clientId: String) = queueMutex.withLock {
        savePending(loadPending().filterNot { it.clientId == clientId })
    }

    /** Kirim catatan tertunda. Hanya penolakan 4xx yang membuang catatan dari antrean. */
    suspend fun syncPending(): Int = queueMutex.withLock {
        val pending = loadPending()
        if (pending.isEmpty()) return@withLock 0
        val remaining = mutableListOf<PendingNote>()
        var sent = 0
        for ((index, note) in pending.withIndex()) {
            val result = send(note)
            if (result.isSuccess) {
                sent++
            } else if ((result.exceptionOrNull() as? ApiException)?.retryable == true) {
                remaining += pending.drop(index)
                break
            }
        }
        savePending(remaining)
        sent
    }

    private suspend fun send(note: PendingNote): Result<Note> =
        device.authed { createNote(CreateNoteRequest(note.content, note.taskId, note.clientId)) }

    private suspend fun loadPending(): List<PendingNote> = decode(settings.pendingNotes.first())

    private fun decode(json: String?): List<PendingNote> {
        if (json == null) return emptyList()
        return runCatching {
            gson.fromJson<List<PendingNote>>(json, object : TypeToken<List<PendingNote>>() {}.type)
        }.getOrNull().orEmpty()
    }

    private suspend fun savePending(list: List<PendingNote>) {
        settings.setPendingNotes(if (list.isEmpty()) null else gson.toJson(list))
    }
}
