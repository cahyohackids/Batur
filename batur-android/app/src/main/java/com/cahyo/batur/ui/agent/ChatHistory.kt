package com.cahyo.batur.ui.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Simpan/pulihkan chat Agent sebagai JSON. ChatMessage berbentuk sealed interface, jadi disimpan
 * lewat satu bentuk datar supaya Gson tidak perlu tahu subtipe.
 */
object ChatHistory {
    const val MAX_MESSAGES = 50

    private val gson = Gson()

    private data class Stored(
        val kind: String,
        val id: Long,
        val text: String? = null,
        val mode: String? = null,
        val tone: String? = null,
        val taskId: Int? = null,
        val title: String? = null,
        val skillName: String? = null,
        val icon: String? = null,
        val functionName: String? = null,
        val details: List<List<String>>? = null,
        val mechanism: String? = null,
        val state: String? = null,
    )

    fun encode(messages: List<ChatMessage>): String? {
        val stored = messages.mapNotNull(::toStored).takeLast(MAX_MESSAGES)
        return if (stored.isEmpty()) null else gson.toJson(stored)
    }

    /**
     * Aksi yang dulu menunggu konfirmasi (atau sedang jalan saat aplikasi ditutup) dipulihkan
     * sebagai kedaluwarsa: tidak bisa dijalankan lagi karena aksinya sudah tidak ada di memori.
     */
    fun decode(json: String?): List<ChatMessage> {
        if (json.isNullOrBlank()) return emptyList()
        val stored = runCatching {
            gson.fromJson<List<Stored>>(json, object : TypeToken<List<Stored>>() {}.type)
        }.getOrNull().orEmpty()
        return stored.mapNotNull(::fromStored)
    }

    private fun toStored(m: ChatMessage): Stored? = when (m) {
        is ChatMessage.User -> Stored("user", m.id, text = m.text)
        is ChatMessage.Thinking -> null
        is ChatMessage.Assistant -> Stored("assistant", m.id, text = m.text, mode = m.mode, tone = m.tone.name, taskId = m.taskId)
        is ChatMessage.Confirmation -> Stored(
            "confirmation",
            m.id,
            mode = m.mode,
            title = m.title,
            skillName = m.skillName,
            icon = m.icon,
            functionName = m.functionName,
            details = m.details.map { listOf(it.first, it.second) },
            mechanism = m.mechanism,
            state = m.state.name,
        )
    }

    private fun fromStored(s: Stored): ChatMessage? = when (s.kind) {
        "user" -> s.text?.let { ChatMessage.User(s.id, it) }
        "assistant" -> s.text?.let {
            ChatMessage.Assistant(
                id = s.id,
                text = it,
                mode = s.mode,
                tone = Tone.entries.firstOrNull { t -> t.name == s.tone } ?: Tone.NORMAL,
                taskId = s.taskId,
            )
        }
        "confirmation" -> {
            val state = ConfirmState.entries.firstOrNull { it.name == s.state } ?: ConfirmState.EXPIRED
            ChatMessage.Confirmation(
                id = s.id,
                title = s.title ?: "Aksi",
                skillName = s.skillName,
                icon = s.icon ?: "",
                functionName = s.functionName,
                details = s.details.orEmpty().filter { it.size == 2 }.map { it[0] to it[1] },
                mechanism = s.mechanism.orEmpty(),
                mode = s.mode ?: "cloud",
                state = if (state == ConfirmState.PENDING || state == ConfirmState.RUNNING) ConfirmState.EXPIRED else state,
            )
        }
        else -> null
    }
}
