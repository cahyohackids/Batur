package com.cahyo.batur.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.agent.AgentOrchestrator
import com.cahyo.batur.agent.Decision
import com.cahyo.batur.agent.EngineState
import com.cahyo.batur.agent.OnDeviceAgentEngine
import com.cahyo.batur.data.remote.ApiException
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.repository.RecordOutcome
import com.cahyo.batur.speech.SpeechPlayer
import com.cahyo.batur.speech.SpeechState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Tone { NORMAL, SUCCESS, ERROR }

/** EXPIRED: aksi dari sesi sebelumnya yang dipulihkan dari riwayat chat; tidak bisa dijalankan. */
enum class ConfirmState { PENDING, RUNNING, DONE, FAILED, CANCELLED, EXPIRED }

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
        /** function_name skill, misalnya save_note untuk tombol "Lihat catatan". */
        val functionName: String?,
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

@OptIn(FlowPreview::class)
class AgentViewModel(
    private val orchestrator: AgentOrchestrator,
    private val settings: SettingsStore,
    onDeviceEngine: OnDeviceAgentEngine,
    private val speech: SpeechPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    /** Untuk label "Memuat model…" saat model on-device sedang dimuat ke memori. */
    val engineState: StateFlow<EngineState> = onDeviceEngine.state

    /** Pesan yang sedang dibacakan, untuk ikon speaker. */
    val speechState: StateFlow<SpeechState> = speech.state

    private val autoSpeak: StateFlow<Boolean> = settings.autoSpeak
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var thinkingJob: Job? = null
    private val pendingActions = mutableMapOf<Long, Decision.Action>()
    private var lastId = 0L
    private fun nextId() = ++lastId

    init {
        orchestrator.warmUp()
        viewModelScope.launch {
            val restored = ChatHistory.decode(settings.chatHistory())
            lastId = maxOf(lastId, restored.maxOfOrNull { it.id } ?: 0L)
            _state.update { it.copy(messages = restored + it.messages) }
            // Simpan setelah pemulihan selesai, supaya riwayat lama tidak tertimpa daftar kosong.
            _state.map { it.messages }
                .distinctUntilChanged()
                .debounce(400)
                .collect { settings.setChatHistory(ChatHistory.encode(it)) }
        }
    }

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

    fun send(raw: String) {
        val command = raw.trim()
        if (command.isEmpty() || !_state.value.canSend) return
        if (command.length > MAX_COMMAND_LENGTH) {
            append(ChatMessage.Assistant(nextId(), "Perintahnya kepanjangan. Maksimal $MAX_COMMAND_LENGTH karakter.", tone = Tone.ERROR))
            return
        }

        speech.stop()
        val thinkingId = nextId()
        _state.update {
            it.copy(
                busy = true,
                messages = it.messages + ChatMessage.User(nextId(), command) + ChatMessage.Thinking(thinkingId),
            )
        }

        thinkingJob = viewModelScope.launch {
            val result = orchestrator.decide(command)
            removeMessage(thinkingId)
            result.fold(
                onSuccess = { handle(it) },
                onFailure = { appendReply(ChatMessage.Assistant(nextId(), failureText(it), tone = Tone.ERROR)) },
            )
            _state.update { it.copy(busy = false) }
        }
    }

    /**
     * Batalkan perintah yang sedang diproses. Inferensi native tidak bisa dihentikan di tengah jalan,
     * tapi hasilnya dibuang karena coroutine-nya sudah dibatalkan.
     */
    fun cancelThinking() {
        val job = thinkingJob ?: return
        if (!job.isActive || _state.value.messages.none { it is ChatMessage.Thinking }) return
        job.cancel()
        thinkingJob = null
        _state.update { s ->
            s.copy(busy = false, messages = s.messages.filterNot { it is ChatMessage.Thinking })
        }
        append(ChatMessage.Assistant(nextId(), "Oke, perintah dibatalkan."))
    }

    private fun failureText(e: Throwable): String = when {
        e is ApiException && e.code == "quota_exceeded" -> buildString {
            append(e.message.orEmpty())
            if (e.message?.contains("on-device", ignoreCase = true) != true) {
                append(" Pakai mode on-device: unduh model di tab Model, lalu pilih Otomatis.")
            }
        }
        // Server menjawab tapi menolak (dibatasi, data tidak valid): pesan server sudah jelas.
        e is ApiException && e.httpCode != null -> e.message.orEmpty()
        e is IllegalStateException -> e.message.orEmpty()
        else -> "Gagal menghubungi Batur API. ${e.message}"
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
                        functionName = action.functionName,
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
        speech.stop()
        _state.value = AgentUiState()
    }

    fun toggleSpeech(message: ChatMessage.Assistant) = speech.toggle(message.id, message.text)

    fun stopSpeech() = speech.stop()

    override fun onCleared() {
        speech.stop()
    }

    companion object {
        /** Sama dengan batas server untuk POST /api/agent/cloud. */
        const val MAX_COMMAND_LENGTH = 500
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

    /** Catat ke Riwayat, tampilkan pesan hasil, lalu catatan kecil kalau riwayat tertunda atau gagal. */
    private suspend fun recordThen(
        decision: Decision,
        status: String,
        resultText: String?,
        message: (taskId: Int?) -> ChatMessage,
    ) {
        when (val outcome = orchestrator.record(decision.trace, status, resultText)) {
            is RecordOutcome.Saved -> appendReply(message(outcome.id))
            RecordOutcome.Queued -> {
                appendReply(message(null))
                append(ChatMessage.Assistant(nextId(), "Riwayat belum bisa dikirim ke server. Disimpan dulu di ponsel dan dikirim otomatis nanti."))
            }
            is RecordOutcome.Failed -> {
                appendReply(message(null))
                append(ChatMessage.Assistant(nextId(), "Riwayat tidak tersimpan: ${outcome.message}", tone = Tone.ERROR))
            }
        }
    }

    private fun append(message: ChatMessage) {
        _state.update { it.copy(messages = it.messages + message) }
    }

    /** Jawaban utama Batur: dibacakan otomatis kalau pengaturannya aktif. Catatan kecil tidak. */
    private fun appendReply(message: ChatMessage) {
        append(message)
        if (autoSpeak.value && message is ChatMessage.Assistant) speech.speak(message.id, message.text)
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
