package com.cahyo.batur.agent

import android.content.Context
import android.os.SystemClock
import com.cahyo.batur.agent.executor.ExecContext
import com.cahyo.batur.agent.executor.ExecResult
import com.cahyo.batur.agent.executor.InvalidArgumentsException
import com.cahyo.batur.agent.executor.PreparedAction
import com.cahyo.batur.agent.executor.SkillExecutors
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiException
import com.cahyo.batur.data.remote.CreateTaskRequest
import com.cahyo.batur.data.remote.FunctionCall
import com.cahyo.batur.data.remote.SkillSummary
import com.cahyo.batur.data.remote.StepRequest
import com.cahyo.batur.data.repository.NoteRepository
import com.cahyo.batur.data.repository.SkillRepository
import com.cahyo.batur.data.repository.RecordOutcome
import com.cahyo.batur.data.repository.TaskRepository
import com.cahyo.batur.ui.common.formatFunctionCall
import com.cahyo.batur.ui.common.formatLatency
import com.cahyo.batur.ui.common.modeLabel
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Chat tidak merender Markdown; buang penanda yang paling sering muncul (**tebal**, # judul, `kode`). */
internal fun stripMarkdown(text: String): String = text
    .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    .replace(Regex("""__(.+?)__"""), "$1")
    .replace(Regex("""(?m)^#{1,6}\s+"""), "")
    .replace("`", "")
    .trim()

private val DURATION_PART = Regex("""(\d+(?:[.,]\d+)?)\s*(jam|menit|mnt|detik|dtk)\b""", RegexOption.IGNORE_CASE)

/** "Catat: beli susu" -> "beli susu". Hanya bentuk eksplisit dengan titik dua. */
internal fun explicitNote(text: String): String? =
    Regex("""^\s*(?:catat(?:an)?|simpan\s+catatan|tulis\s+catatan)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

/** "1 jam 30 menit" -> 5400. Null kalau perintah tidak menyebut durasi berangka. */
internal fun parseDurationSeconds(text: String): Long? {
    val parts = DURATION_PART.findAll(text).toList()
    if (parts.isEmpty()) return null
    return parts.sumOf { m ->
        val n = m.groupValues[1].replace(',', '.').toDouble()
        val unit = when (m.groupValues[2].lowercase()) {
            "jam" -> 3600
            "menit", "mnt" -> 60
            else -> 1
        }
        (n * unit).toLong()
    }
}

/** Jejak satu perintah: dikumpulkan selama alur berjalan, lalu dikirim ke POST /api/tasks. */
class TaskTrace(val command: String) {
    var mode: String = "cloud"
    var modelSlug: String? = null
    var skillSlug: String? = null
    var functionCall: FunctionCall? = null
    var latencyMs: Int? = null
    private val steps = mutableListOf(StepRequest("input", command))

    fun add(type: String, content: String) {
        steps += StepRequest(type, content)
    }

    fun toRequest(status: String, resultText: String?) = CreateTaskRequest(
        command = command,
        mode = mode,
        modelSlug = modelSlug,
        skillSlug = skillSlug,
        functionCall = functionCall,
        status = status,
        resultText = resultText,
        latencyMs = latencyMs,
        steps = steps.toList(),
    )
}

sealed interface Decision {
    val trace: TaskTrace

    /** Model menjawab dengan teks biasa. */
    data class Answer(override val trace: TaskTrace, val text: String) : Decision

    /** Model memilih skill dan argumennya valid. */
    class Action(
        override val trace: TaskTrace,
        val prepared: PreparedAction,
        val requiresConfirmation: Boolean,
        val skillName: String?,
    ) : Decision

    /** Tool call tidak bisa dijalankan (skill tak dikenal / argumen kurang). */
    data class NotUnderstood(override val trace: TaskTrace, val reason: String) : Decision
}

/**
 * Alur satu perintah (spec §3.1): engine → validasi → (konfirmasi di UI) → executor → catat ke API.
 *
 * Urutan engine pada mode "auto": on-device dulu kalau model aktif sudah terunduh. Kalau model
 * on-device gagal, memilih skill yang tidak valid, atau tidak memilih skill sama sekali, perintah
 * diteruskan ke cloud (server sendiri jatuh ke parser aturan kalau gateway mati). Kalau cloud juga
 * tidak terjangkau, jawaban teks dari on-device tetap dipakai.
 */
class AgentOrchestrator(
    appContext: Context,
    private val cloudEngine: AgentEngine,
    private val onDeviceEngine: OnDeviceAgentEngine,
    private val skillRepository: SkillRepository,
    private val taskRepository: TaskRepository,
    noteRepository: NoteRepository,
    private val settings: SettingsStore,
) {
    private val execContext = ExecContext(appContext, noteRepository)
    private var skills: List<SkillSummary>? = null
    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Muat model on-device di latar belakang kalau mode Otomatis dipakai. */
    fun warmUp() {
        warmScope.launch {
            if (settings.engineMode.first() == SettingsStore.ENGINE_AUTO) onDeviceEngine.warmUp()
        }
    }

    /** Lepas model on-device dari memori, misalnya saat pengguna memilih "Selalu Cloud". */
    suspend fun releaseOnDevice() = onDeviceEngine.release()

    /** @return failure hanya kalau tidak ada engine yang bisa dipakai sama sekali. */
    suspend fun decide(command: String): Result<Decision> {
        val trace = TaskTrace(command)
        val start = SystemClock.elapsedRealtime()
        fun finish(d: Decision): Result<Decision> {
            trace.latencyMs = (SystemClock.elapsedRealtime() - start).toInt()
            return Result.success(d)
        }

        var onDeviceText: String? = null
        var onDeviceRejected: String? = null
        val useOnDevice = settings.engineMode.first() == SettingsStore.ENGINE_AUTO && onDeviceEngine.readyModel() != null
        if (useOnDevice) {
            onDeviceEngine.run(command).fold(
                onSuccess = { output ->
                    trace.applyEngine(output)
                    trace.add("reasoning", describeEngine(output))
                    when (val result = interpret(output, trace)) {
                        is Interpretation.Ok -> return finish(result.decision)
                        is Interpretation.Text -> {
                            onDeviceText = result.text
                            trace.add("reasoning", "Model on-device tidak memilih skill; perintah diteruskan ke Cloud.")
                        }
                        is Interpretation.Invalid -> {
                            onDeviceRejected = result.reason
                            trace.add("error", result.reason)
                            trace.add("reasoning", "Pilihan model on-device tidak bisa dijalankan; dicoba ulang lewat Cloud.")
                        }
                    }
                },
                onFailure = { e ->
                    if (e !is OnDeviceSkippedException) {
                        trace.add("error", "Model on-device gagal: ${e.message ?: e.javaClass.simpleName}")
                    }
                },
            )
        }

        val cloud = cloudEngine.run(command).getOrElse { e ->
            onDeviceText?.let { text ->
                trace.add("reasoning", "Cloud tidak terjangkau; jawaban model on-device dipakai.")
                trace.skillSlug = null
                trace.functionCall = null
                trace.add("response", text)
                return finish(Decision.Answer(trace, text))
            }
            explicitNote(command)?.let { content ->
                trace.add("reasoning", "Server tidak terjangkau; perintah \"Catat:\" dikenali langsung di ponsel.")
                val local = EngineOutput(
                    mode = "fallback", modelSlug = null, type = "tool_call",
                    functionCall = FunctionCall("save_note", JsonObject().apply { addProperty("content", content) }),
                    skillSlug = "save-note", text = null, latencyMs = 0,
                )
                trace.applyEngine(local)
                (interpret(local, trace) as? Interpretation.Ok)?.let { return finish(it.decision) }
            }
            onDeviceRejected?.let { return finish(Decision.NotUnderstood(trace, it)) }
            // Server terjangkau tapi menolak (misalnya kuota habis): pesan server lebih berguna.
            if (useOnDevice && (e as? ApiException)?.httpCode == null) {
                return Result.failure(
                    IllegalStateException("Model on-device belum bisa memproses perintah ini dan server juga tidak terjangkau. Coba kalimat lain atau sambungkan internet."),
                )
            }
            return Result.failure(e)
        }
        trace.applyEngine(cloud)
        trace.skillSlug = null
        trace.functionCall = null
        trace.add("reasoning", describeEngine(cloud))
        return when (val result = interpret(cloud, trace)) {
            is Interpretation.Ok -> finish(result.decision)
            is Interpretation.Text -> {
                trace.add("response", result.text)
                finish(Decision.Answer(trace, result.text))
            }
            is Interpretation.Invalid -> {
                trace.add("error", result.reason)
                finish(Decision.NotUnderstood(trace, result.reason))
            }
        }
    }

    private sealed interface Interpretation {
        class Ok(val decision: Decision) : Interpretation
        class Text(val text: String) : Interpretation
        class Invalid(val reason: String) : Interpretation
    }

    /** Ubah keluaran engine menjadi aksi siap-konfirmasi, teks, atau alasan tidak valid. */
    private suspend fun interpret(output: EngineOutput, trace: TaskTrace): Interpretation {
        val call = output.functionCall
        if (output.type != "tool_call" || call == null) {
            // Teks yang berupa JSON adalah tool call yang gagal dibaca, bukan jawaban untuk pengguna.
            val text = output.text?.let(::stripMarkdown)?.takeIf { it.isNotBlank() && !it.startsWith("{") }
                ?: "Maaf, Batur belum paham perintah itu."
            return Interpretation.Text(text)
        }

        trace.add("tool_call", formatFunctionCall(call.name, call.args))
        if (call.name == "set_timer" && parseDurationSeconds(trace.command) == null) {
            return Interpretation.Invalid("Perintah tidak menyebut durasi, jadi bukan perintah timer.")
        }
        val checked = checkArguments(trace.command, call)
        if (checked !== call) {
            trace.add("reasoning", "Durasi dari model tidak cocok dengan perintah; dikoreksi menjadi ${checked.args?.get("seconds")} detik.")
        }
        val executor = SkillExecutors.find(call.name)
            ?: return Interpretation.Invalid("Skill ${call.name} belum didukung aplikasi ini.")
        val prepared = try {
            executor.prepare(checked.args ?: JsonObject())
        } catch (e: InvalidArgumentsException) {
            return Interpretation.Invalid("Argumen dari model tidak lengkap: ${e.message}.")
        }

        val skill = skillFor(call.name)
        trace.skillSlug = output.skillSlug ?: skill?.slug
        trace.functionCall = checked
        return Interpretation.Ok(
            Decision.Action(
                trace = trace,
                prepared = prepared,
                requiresConfirmation = skill?.requiresConfirmation ?: executor.defaultConfirmation,
                skillName = skill?.name,
            ),
        )
    }

    /**
     * Pemeriksaan deterministik untuk argumen yang sering salah dihitung model kecil.
     * Saat ini hanya durasi timer: kalau perintah menyebut "25 menit" tapi model memberi 15000 detik,
     * angka dari perintah yang dipakai. Kartu konfirmasi tetap menampilkan hasil akhirnya.
     */
    private fun checkArguments(command: String, call: FunctionCall): FunctionCall {
        if (call.name != "set_timer") return call
        val parsed = parseDurationSeconds(command) ?: return call
        val given = call.args?.get("seconds")?.takeIf { it.isJsonPrimitive }?.asDouble?.toLong()
        if (given == parsed) return call
        val args = (call.args?.deepCopy() ?: JsonObject()).apply { addProperty("seconds", parsed) }
        return FunctionCall(call.name, args)
    }

    private fun TaskTrace.applyEngine(output: EngineOutput) {
        mode = output.mode
        modelSlug = output.modelSlug
    }

    suspend fun execute(action: Decision.Action): ExecResult = try {
        action.prepared.execute(execContext)
    } catch (e: Exception) {
        ExecResult(false, "Aksi gagal dijalankan: ${e.message ?: e.javaClass.simpleName}", action.prepared.mechanism)
    }

    /** Simpan ke Riwayat, atau antrekan di ponsel kalau server tidak terjangkau. */
    suspend fun record(trace: TaskTrace, status: String, resultText: String?): RecordOutcome =
        taskRepository.record(trace.toRequest(status, resultText))

    private suspend fun skillFor(functionName: String): SkillSummary? {
        if (skills == null) skills = skillRepository.getSkills().getOrNull()
        return skills?.firstOrNull { it.functionName == functionName }
    }

    private fun describeEngine(output: EngineOutput): String {
        val latency = formatLatency(output.latencyMs)
        return when (output.mode) {
            "cloud" -> "Diproses mode Cloud lewat Batur API dalam $latency"
            "fallback" -> "Gateway AI tidak tersedia; parser aturan di server dipakai ($latency)"
            OnDeviceAgentEngine.MODE -> buildString {
                append("Diproses On-device dengan ${output.modelSlug}")
                onDeviceEngine.backendLabel?.let { append(" ($it)") }
                append(" langsung di ponsel dalam $latency")
            }
            else -> "Diproses mode ${modeLabel(output.mode)} dalam $latency"
        }
    }
}
