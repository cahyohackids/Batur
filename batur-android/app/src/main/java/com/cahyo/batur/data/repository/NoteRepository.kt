package com.cahyo.batur.data.repository

import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiClient
import com.cahyo.batur.data.remote.CreateNoteRequest
import com.cahyo.batur.data.remote.Note
import com.cahyo.batur.data.remote.apiCall

class NoteRepository(
    private val client: ApiClient,
    private val settings: SettingsStore,
) {

    suspend fun getNotes(): Result<List<Note>> {
        val deviceId = settings.deviceId()
        return apiCall { client.api().getNotes(deviceId) }
    }

    suspend fun createNote(content: String, taskId: Int? = null): Result<Note> {
        val deviceId = settings.deviceId()
        return apiCall { client.api().createNote(CreateNoteRequest(deviceId, content, taskId)) }
    }
}
