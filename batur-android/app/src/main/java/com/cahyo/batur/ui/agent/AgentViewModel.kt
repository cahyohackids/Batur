package com.cahyo.batur.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.agent.AgentOrchestrator
import com.cahyo.batur.agent.Decision
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.repository.RecordOutcome
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Tone { NORMAL, SUCCESS, ERROR }

enum class ConfirmState { PENDING, RUNNING, DONE, FAILED, CANCELLED }

sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage

    data class Thinking(override val id: Long) : ChatMessage

    data class Assistant(
        override val id: Long,
        val text: String,
        val mode: String? = null,
        val tone: Tone = Tone.NORMAL,
        /** Id tugas di Riwayat, untuk tombol "Lihat jejak". */
        val taskId: Int? = null,
    ) : ChatMessage

    data class Confirmation(
        override val id: Long,
        val title: String,
        val skillName: String?,
        val icon: String,
        val details: List<Pair<String, String>>,
        val mechanism: String,
        val mode: String,
        val state: ConfirmState,
    ) : ChatMessage
}

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
) {
    val hasPendingConfirmation: Boolean
        get() = messages.any { it is ChatMessage.Confirmation && it.state == ConfirmState.PENDING }
    val canSend: Boolean get() = !busy && !hasPendingConfirmation
}

/** Engine yang akan dipakai untuk perintah berikutnya, ditampilkan di chip atas layar Agent. */
data class EngineChoice(val mode: String, val onDeviceModel: String?) {
    val usesOnDevice: Boolean get() = mode == SettingsStore.ENGINE_AUTO && onDeviceModel != null
}

class AgentViewModel(
    private val orchestrator: AgentOrchestrator,
    private val settings: SettingsStore,
) : ViewModel() {

    init {
        orchestrator.warmUp()
    }

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    val engine: StateFlow<EngineChoice> = combine(settings.engineMode, settings.activeModel) { mode, model ->
        EngineChoice(mode, model?.name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngineChoice(SettingsStore.ENGINE_AUTO, null))

    fun setEngineMode(mode: String) {
        viewModelScope.launch {
            settings.setEngineMode(mode)
            // Mode Cloud tidak butuh model di memori; mode Otomatis langsung memuatnya lagi di latar belakang.
            if (mode == SettingsStore.ENGINE_CLOUD) orchestrator.releaseOnDevice() else orchestrator.warmUp()
        }
    }

    private val pendingActions = mutableMapOf<Long, Decision.Action>()
    private var lastId = 0L
    private fun nextId() = ++lastId

    fun send(raw: String) {
        val command = raw.trim()
        if (command.isEmpty() || !_state.value.canSend) return

        val thinkingId = nextId()
        _state.update {
            it.copy(
                busy = true,
                messages = it.messages + ChatMessage.User(nextId(), command) + ChatMessage.Thinking(thinkingId),
            )
        }

        viewModelScope.launch {
            val result = orchestrator.decide(command)
            removeMessage(thinkingId)
            result.fold(
                onSuccess = { handle(it) },
                onFailure = {
                    val text = if (it is IllegalStateException) it.message.orEmpty() else "Gagal menghubungi Batur API. ${it.message}"
                    append(ChatMessage.Assistant(nextId(), text, tone = Tone.ERROR))
                },
            )
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun handle(decision: Decision) {
        when (decision) {
            is Decision.Answer -> {
                recordThen(decision, "answered", decision.text) { taskId ->
                    ChatMessage.Assistant(nextId(), decision.text, mode = decision.trace.mode, taskId = taskId)
                }
            }

            is Decision.NotUnderstood -> {
                recordThen(decision, "failed", decision.reason) { taskId ->
                    ChatMessage.Assistant(
                        nextId(),
                        "Maaf, Batur belum bisa menjalankan itu. ${decision.reason}",
                        mode = decision.trace.mode,
                        tone = Tone.ERROR,
                        taskId = taskId,
                    )
                }
            }

            is Decision.Action -> {
                val id = nextId()
                val action = decision.prepared
                append(
                    ChatMessage.Confirmation(
                        id = id,
                        title = action.title,
                        skillName = decision.skillName,
                        icon = action.icon,
                        details = action.details,
                        mechanism = action.mechanism,
                        mode = decision.trace.mode,
                        state = if (decision.requiresConfirmation) ConfirmState.PENDING else ConfirmState.RUNNING,
                    ),
                )
                if (decision.requiresConfirmation) {
                    pendingActions[id] = decision
                } else {
                    decision.trace.add("confirmation", "Tidak perlu konfirmasi (skill aman)")
                    run(id, decision)
                }
            }
        }
    }

    fun confirm(messageId: Long) {
        val decision = pendingActions.remove(messageId) ?: return
        decision.trace.add("confirmation", "Pengguna menyetujui")
        setConfirmState(messageId, ConfirmState.RUNNING)
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            run(messageId, decision)
            _state.update { it.copy(busy = false) }
        }
    }

    fun cancel(messageId: Long) {
        val decision = pendingActions.remove(messageId) ?: return
        decision.trace.add("confirmation", "Pengguna membatalkan")
        decision.trace.add("response", "Dibatalkan pengguna.")
        setConfirmState(messageId, ConfirmState.CANCELLED)
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            recordThen(decision, "cancelled", "Dibatalkan pengguna.") { taskId ->
                ChatMessage.Assistant(nextId(), "Oke, dibatalkan.", mode = decision.trace.mode, taskId = taskId)
            }
            _state.update { it.copy(busy = false) }
        }
    }

    fun clear() {
        if (!_state.value.canSend) return
        _state.value = AgentUiState()
    }

    private suspend fun run(messageId: Long, decision: Decision.Action) {
        val result = orchestrator.execute(decision)
        decision.trace.add("execution", result.mechanism)
        decision.trace.add(if (result.success) "response" else "error", result.message)
        setConfirmState(messageId, if (result.success) ConfirmState.DONE else ConfirmState.FAILED)
        recordThen(decision, if (result.success) "success" else "failed", result.message) { taskId ->
            ChatMessage.Assistant(
                nextId(),
                result.message,
                mode = decision.trace.mode,
                tone = if (result.success) Tone.SUCCESS else Tone.ERROR,
                taskId = taskId,
            )
        }
    }

    /** Catat ke Riwayat; kalau gagal, beri tahu di chat tapi alur tetap jalan. */
    /** Catat ke Riwayat, tampilkan pesan hasil, lalu catatan kecil kalau riwayat tertunda atau gagal. */
    private suspend fun recordThen(
        decision: Decision,
        status: String,
        resultText: String?,
        message: (taskId: Int?) -> ChatMessage,
    ) {
        when (val outcome = orchestrator.record(decision.trace, status, resultText)) {
            is RecordOutcome.Saved -> append(message(outcome.id))
            RecordOutcome.Queued -> {
                append(message(null))
                append(ChatMessage.Assistant(nextId(), "Server tidak terjangkau. Riwayat disimpan dulu di ponsel dan dikirim otomatis saat online."))
            }
            is RecordOutcome.Failed -> {
                append(message(null))
                append(ChatMessage.Assistant(nextId(), "Riwayat tidak tersimpan: ${outcome.message}", tone = Tone.ERROR))
            }
        }
    }

    private fun append(message: ChatMessage) {
        _state.update { it.copy(messages = it.messages + message) }
    }

    private fun removeMessage(id: Long) {
        _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
    }

    private fun setConfirmState(id: Long, newState: ConfirmState) {
        _state.update { s ->
            s.copy(
                messages = s.messages.map {
                    if (it is ChatMessage.Confirmation && it.id == id) it.copy(state = newState) else it
                },
            )
        }
    }
}
