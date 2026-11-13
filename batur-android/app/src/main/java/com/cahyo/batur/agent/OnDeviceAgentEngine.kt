package com.cahyo.batur.agent

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.cahyo.batur.data.prefs.ActiveModel
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.FunctionCall
import com.cahyo.batur.data.remote.asObjectOrNull
import com.cahyo.batur.data.remote.ToolDefinition
import com.cahyo.batur.data.repository.SkillRepository
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.tool
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Status model on-device di memori, untuk label "Memuat model…" di Agent. */
enum class EngineState { Idle, Loading, Ready }

/** On-device sengaja dilewati (misalnya pengguna baru memilih "Selalu Cloud"), bukan gagal. */
class OnDeviceSkippedException(message: String) : IllegalStateException(message)

/**
 * Mode on-device: model .litertlm dijalankan LiteRT-LM langsung di ponsel.
 *
 * Model hanya memilih skill (tool call) atau menjawab teks; eksekusinya tetap lewat
 * executor + kartu konfirmasi yang sama dengan mode cloud, jadi tool calling otomatis dimatikan.
 */
class OnDeviceAgentEngine(
    context: Context,
    private val settings: SettingsStore,
    private val modelStore: ModelStore,
    private val skillRepository: SkillRepository,
) : AgentEngine {
    private val cacheDir = context.cacheDir.path
    private val gson = Gson()
    private val mutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var engine: Engine? = null
    private var prepared: Prepared? = null

    private class Prepared(val conversation: Conversation, val toolsKey: String)
    private var loadedPath: String? = null

    /** Backend yang berhasil dipakai untuk model yang sedang dimuat (GPU/CPU). */
    var backendLabel: String? = null
        private set

    /** Model aktif yang filenya sudah lengkap, atau null kalau on-device tidak bisa dipakai. */
    suspend fun readyModel(): Pair<ActiveModel, File>? = withContext(Dispatchers.IO) {
        val model = settings.activeModel.first() ?: return@withContext null
        val file = modelStore.readyFile(model.fileName, model.sizeBytes) ?: return@withContext null
        model to file
    }

    /**
     * Dicek ulang di dalam lock: selama menunggu giliran, pengguna bisa saja memilih "Selalu Cloud"
     * atau menghapus file, dan model ±1,7 GB tidak boleh tetap dimuat.
     */
    private suspend fun stillWanted(file: File): Boolean =
        settings.engineMode.first() == SettingsStore.ENGINE_AUTO && readyModel()?.second?.path == file.path

    override suspend fun run(command: String): Result<EngineOutput> {
        val (model, file) = readyModel()
            ?: return Result.failure(IllegalStateException("Belum ada model on-device yang siap."))
        val tools = loadTools()
        val start = SystemClock.elapsedRealtime()
        val result = withContext(Dispatchers.Default) {
            mutex.withLock {
                if (!stillWanted(file)) {
                    return@withLock Result.failure<EngineOutput>(OnDeviceSkippedException("Model on-device tidak dipakai lagi."))
                }
                runCatching {
                    val engine = engineFor(file)
                    takeConversation(engine, tools).use { conversation ->
                        val reply = conversation.sendMessage(withTimestamp(command))
                        val latency = (SystemClock.elapsedRealtime() - start).toInt()
                        Log.i(TAG, "Selesai $latency ms, tool=${reply.toolCalls.size}")
                        val text = reply.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                            .let(::stripThinking)
                        val call = reply.toolCalls.firstOrNull()
                            ?.let { FunctionCall(it.name, normalizeNumbers(gson.toJsonTree(it.arguments).asJsonObject)) }
                            // Model kecil kadang menulis tool call sebagai teks JSON biasa tanpa tag <tool_call>.
                            ?: parseTextToolCall(text, tools.map { it.function.name }.toSet())
                        if (call != null) {
                            EngineOutput(MODE, model.slug, "tool_call", call, null, null, latency)
                        } else {
                            EngineOutput(MODE, model.slug, "text", null, null, text, latency)
                        }
                    }
                }.onFailure { Log.w(TAG, "Inferensi on-device gagal", it) }
            }
        }
        // Siapkan percakapan berikutnya di latar belakang supaya perintah selanjutnya tidak menunggu prefill.
        if (result.isSuccess) prepareInBackground(file, tools)
        return result
    }

    /**
     * Muat model dan siapkan satu percakapan di latar belakang, dipanggil saat layar Agent dibuka.
     * Memuat model bisa butuh beberapa detik (puluhan detik di emulator), jadi lebih baik tidak
     * terjadi saat pengguna sudah menunggu jawaban.
     */
    fun warmUp() {
        scope.launch {
            val (_, file) = readyModel() ?: return@launch
            prepareInBackground(file, loadTools())
        }
    }

    private fun prepareInBackground(file: File, tools: List<ToolDefinition>) {
        scope.launch {
            mutex.withLock {
                if (!stillWanted(file)) return@withLock
                runCatching {
                    val engine = engineFor(file)
                    val key = toolsKey(tools)
                    if (prepared?.toolsKey == key) return@runCatching
                    prepared?.conversation?.close()
                    prepared = null
                    val started = SystemClock.elapsedRealtime()
                    prepared = Prepared(engine.createConversation(conversationConfig(tools, prefill = true)), key)
                    Log.i(TAG, "Percakapan disiapkan dalam ${SystemClock.elapsedRealtime() - started} ms")
                }.onFailure { Log.w(TAG, "Gagal menyiapkan model", it) }
            }
        }
    }

    /** Pakai percakapan yang sudah disiapkan kalau daftar tool-nya sama; kalau tidak, buat baru. */
    private fun takeConversation(engine: Engine, tools: List<ToolDefinition>): Conversation {
        val ready = prepared
        prepared = null
        if (ready != null) {
            if (ready.toolsKey == toolsKey(tools)) return ready.conversation
            ready.conversation.close()
        }
        return engine.createConversation(conversationConfig(tools, prefill = false))
    }

    private fun conversationConfig(tools: List<ToolDefinition>, prefill: Boolean) = ConversationConfig(
        systemInstruction = Contents.of(SYSTEM_PROMPT),
        tools = tools.map { tool(SkillTool(it, gson)) },
        samplerConfig = SamplerConfig(1, 0.95, 0.0, 0),
        automaticToolCalling = false,
        prefillPrefaceOnInit = prefill,
        // Tool call cukup puluhan token. Jawaban teks hanya cadangan saat Cloud tak terjangkau,
        // jadi dibatasi supaya perintah non-skill tidak lama tertahan di model kecil.
        maxOutputToken = MAX_OUTPUT_TOKENS,
        thinkingConfig = ThinkingConfig(false),
    )

    private fun toolsKey(tools: List<ToolDefinition>): String = gson.toJson(tools)

    /** Lepas model dari memori, misalnya saat file dihapus atau model aktif diganti. */
    suspend fun release() = mutex.withLock {
        prepared?.conversation?.close()
        prepared = null
        engine?.close()
        engine = null
        loadedPath = null
        backendLabel = null
        _state.value = EngineState.Idle
    }

    private fun engineFor(file: File): Engine {
        engine?.let { if (loadedPath == file.path) return it }
        engine?.close()
        engine = null
        _state.value = EngineState.Loading

        var lastError: Throwable? = null
        for ((label, backend) in backends()) {
            val candidate = Engine(EngineConfig(modelPath = file.path, backend = backend, maxNumTokens = MAX_TOKENS, cacheDir = cacheDir))
            try {
                candidate.initialize()
                engine = candidate
                loadedPath = file.path
                backendLabel = label
                _state.value = EngineState.Ready
                return candidate
            } catch (e: Throwable) {
                Log.w(TAG, "Backend $label gagal", e)
                runCatching { candidate.close() }
                lastError = e
            }
        }
        _state.value = EngineState.Idle
        throw IllegalStateException("Model tidak bisa dimuat: ${lastError?.message}", lastError)
    }

    /** GPU lebih cepat di ponsel asli; emulator tidak punya OpenCL, jadi langsung CPU. */
    private fun backends(): List<Pair<String, Backend>> =
        if (isEmulator()) listOf("CPU" to Backend.CPU())
        else listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())

    private suspend fun loadTools(): List<ToolDefinition> {
        val fresh = withTimeoutOrNull(TOOLS_TIMEOUT_MS) { skillRepository.getTools().getOrNull() }
        if (fresh != null) {
            settings.setCachedTools(gson.toJson(fresh))
            return fresh
        }
        val cached = settings.cachedTools() ?: return emptyList()
        return runCatching {
            gson.fromJson<List<ToolDefinition>>(cached, object : TypeToken<List<ToolDefinition>>() {}.type)
        }.getOrDefault(emptyList())
    }

    /**
     * Waktu sekarang ditaruh di pesan pengguna, bukan di system prompt, supaya percakapan yang
     * sudah disiapkan (prompt + tool sudah di-prefill) tetap benar walau dipakai beberapa menit kemudian.
     */
    private fun withTimestamp(command: String): String {
        val now = ZonedDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
        return "[Sekarang ${now.format(formatter)}, tanggal ${now.toLocalDate()}]\n$command"
    }

    /** Bungkus definisi tool dari Batur API ke format OpenAPI yang dibaca LiteRT-LM. */
    private class SkillTool(private val definition: ToolDefinition, private val gson: Gson) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = gson.toJson(
            JsonObject().apply {
                addProperty("name", definition.function.name)
                addProperty("description", definition.function.description)
                add("parameters", definition.function.parameters.asObjectOrNull() ?: JsonObject())
            },
        )

        // Tidak dipanggil karena automaticToolCalling = false; eksekusi lewat executor + konfirmasi.
        override fun execute(paramsJsonString: String): String = """{"status":"menunggu konfirmasi pengguna"}"""
    }

    companion object {
        const val MODE = "on_device"
        private const val TAG = "OnDeviceAgent"
        private const val MAX_TOKENS = 2048
        private const val MAX_OUTPUT_TOKENS = 160
        private const val TOOLS_TIMEOUT_MS = 2_500L

        private val SYSTEM_PROMPT = """
            Kamu Batur, asisten di ponsel Android. Waktu sekarang tertulis di awal pesan pengguna.
            Kalau perintah cocok dengan salah satu tool, panggil tool itu dengan argumen lengkap.
            Jam memakai format 24 jam: "jam 5 sore" = 17, "jam 7 malam" = 19, "setengah 6" = 5:30.
            Tanggal memakai format YYYY-MM-DD, jam mulai memakai HH:mm.
            Argumen harus JSON valid dengan angka jadi, bukan rumus: timer 10 menit = 600, 25 menit = 1500, 1 jam = 3600 detik.
            Untuk catatan dan pesan WhatsApp, tulis isinya saja persis sesuai maksud pengguna, tanpa kata perintah seperti "catat".
            Kalau tidak ada tool yang cocok, jawab singkat dalam bahasa Indonesia santai tanpa Markdown.
        """.trimIndent()

        private val THINK_BLOCK = Regex("""(?s)<think>.*?</think>""")

        private val FENCE = Regex("""(?s)```(?:json)?|</?tool_call>""")

        /** Kenali {"name": ..., "arguments": {...}} yang ditulis sebagai teks, hanya untuk tool yang memang ada. */
        fun parseTextToolCall(text: String, toolNames: Set<String>): FunctionCall? {
            val body = text.replace(FENCE, "").trim()
            if (!body.startsWith("{")) return null
            return runCatching {
                val obj = com.google.gson.JsonParser.parseString(body).asJsonObject
                val name = obj.get("name")?.asString ?: return null
                if (name !in toolNames) return null
                val args = obj.get("arguments")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                FunctionCall(name, normalizeNumbers(args))
            }.getOrNull()
        }

        /** Qwen3 kadang tetap menulis blok <think> walau thinking dimatikan. */
        fun stripThinking(text: String): String =
            text.replace(THINK_BLOCK, "").replace("<think>", "").replace("</think>", "").trim()

        /** Gson mengubah angka di Map menjadi 5.0; ubah balik ke bilangan bulat kalau memang bulat. */
        fun normalizeNumbers(args: JsonObject): JsonObject {
            val out = JsonObject()
            for ((key, value) in args.entrySet()) {
                val n = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                if (n != null && n % 1.0 == 0.0 && kotlin.math.abs(n) < Long.MAX_VALUE) out.addProperty(key, n.toLong())
                else out.add(key, value)
            }
            return out
        }

        private fun isEmulator(): Boolean =
            Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
                Build.FINGERPRINT.contains("generic") || Build.PRODUCT.contains("sdk")
    }
}
