package com.cahyo.batur.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.SpeechRequest
import com.cahyo.batur.data.repository.DeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Pesan mana yang sedang disiapkan atau dibacakan, untuk ikon speaker di layar Agent. */
sealed interface SpeechState {
    val messageId: Long?

    data object Idle : SpeechState {
        override val messageId: Long? = null
    }

    data class Loading(override val messageId: Long) : SpeechState
    data class Playing(override val messageId: Long) : SpeechState
}

/**
 * Membacakan jawaban Batur.
 *
 * Suara utama diambil dari POST /api/tts (suara neural Indonesia, mp3) lalu disimpan di cache
 * aplikasi, jadi jawaban yang sama tidak diunduh dua kali. Kalau server tidak terjangkau, kuota
 * habis, atau pengguna memilih "Bawaan ponsel", Batur memakai TextToSpeech Android yang jalan offline.
 */
class SpeechPlayer(
    context: Context,
    private val devices: DeviceRepository,
    private val settings: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val cacheDir = File(appContext.cacheDir, "tts")

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    private var localTts: TextToSpeech? = null
    private var localReady: CompletableDeferred<Boolean>? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Ketuk speaker: mulai membacakan, atau berhenti kalau pesan itu sedang dibacakan. */
    fun toggle(messageId: Long, text: String) {
        if (_state.value.messageId == messageId) stop() else speak(messageId, text)
    }

    fun speak(messageId: Long, text: String) {
        stop()
        val clean = prepareText(text)
        if (clean.isEmpty()) return
        _state.value = SpeechState.Loading(messageId)
        job = scope.launch {
            val voice = settings.speechVoice.first()
            val file = if (voice == SettingsStore.VOICE_DEVICE) null else cloudAudio(clean, voice)
            if (file != null && play(messageId, file)) return@launch
            speakLocally(messageId, clean)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        localTts?.stop()
        releaseFocus()
        _state.value = SpeechState.Idle
    }

    // ---------- Suara cloud ----------

    /** File mp3 dari cache, atau diunduh dari server. Null kalau gagal (lalu pakai suara ponsel). */
    private suspend fun cloudAudio(text: String, voice: String): File? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, cacheName(text, voice))
        if (file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return@withContext file
        }
        val bytes = withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
            devices.authedBytes { speech(SpeechRequest(text, voice)) }.getOrNull()
        } ?: return@withContext null
        runCatching {
            cacheDir.mkdirs()
            val tmp = File(cacheDir, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) error("rename gagal")
            prune()
            file
        }.getOrNull()
    }

    private fun prune() {
        val files = cacheDir.listFiles { f -> f.name.endsWith(".mp3") } ?: return
        if (files.size <= MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_CACHE_FILES).forEach { it.delete() }
    }

    /** @return false kalau file tidak bisa diputar (rusak), supaya jatuh ke suara ponsel. */
    private fun play(messageId: Long, file: File): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(file.path)
            mp.setOnCompletionListener { finish(messageId) }
            mp.setOnErrorListener { _, _, _ -> finish(messageId); true }
            mp.prepare()
            requestFocus()
            player = mp
            _state.value = SpeechState.Playing(messageId)
            mp.start()
            true
        } catch (e: Exception) {
            mp.release()
            file.delete()
            false
        }
    }

    // ---------- Suara bawaan ponsel ----------

    private suspend fun speakLocally(messageId: Long, text: String) {
        val tts = localEngine()
        if (tts == null) {
            _state.value = SpeechState.Idle
            Toast.makeText(appContext, OFFLINE_VOICE_HINT, Toast.LENGTH_LONG).show()
            return
        }
        requestFocus()
        _state.value = SpeechState.Playing(messageId)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "batur-$messageId")
    }

    private suspend fun localEngine(): TextToSpeech? {
        val ready = localReady ?: CompletableDeferred<Boolean>().also { deferred ->
            localReady = deferred
            localTts = TextToSpeech(appContext) { status -> deferred.complete(status == TextToSpeech.SUCCESS) }
        }
        val ok = withTimeoutOrNull(LOCAL_INIT_TIMEOUT_MS) { ready.await() } ?: false
        val tts = localTts
        if (!ok || tts == null) {
            // Coba lagi lain kali: mesin TTS mungkin baru terpasang atau sedang sibuk.
            tts?.shutdown()
            localTts = null
            localReady = null
            return null
        }
        val lang = tts.setLanguage(Locale.forLanguageTag("id-ID"))
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return null
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finishUtterance(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishUtterance(utteranceId, failed = true)
            override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance(utteranceId, failed = true)
        })
        return tts
    }

    private fun finishUtterance(utteranceId: String?, failed: Boolean = false) {
        val id = utteranceId?.removePrefix("batur-")?.toLongOrNull() ?: return
        scope.launch {
            // Biasanya karena offline dan paket suara Indonesia belum diunduh di mesin TTS ponsel.
            if (failed && _state.value.messageId == id) {
                Toast.makeText(appContext, OFFLINE_VOICE_HINT, Toast.LENGTH_LONG).show()
            }
            finish(id)
        }
    }

    // ---------- Umum ----------

    private fun finish(messageId: Long) {
        if (_state.value.messageId != messageId) return
        player?.release()
        player = null
        releaseFocus()
        _state.value = SpeechState.Idle
    }

    /** Musik/podcast dikecilkan sebentar selama Batur bicara. */
    private fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audioManager?.requestAudioFocus(request)
        focus = request
    }

    private fun releaseFocus() {
        focus?.let { audioManager?.abandonAudioFocusRequest(it) }
        focus = null
    }

    companion object {
        /** Sama dengan batas server untuk POST /api/tts. */
        const val MAX_TEXT = 1000
        private const val MAX_CACHE_FILES = 100
        private const val CLOUD_TIMEOUT_MS = 20_000L
        private const val LOCAL_INIT_TIMEOUT_MS = 5_000L
        private const val OFFLINE_VOICE_HINT =
            "Suara tidak bisa diputar. Sambungkan internet, atau unduh paket suara Bahasa Indonesia " +
                "di Pengaturan > Text-to-speech."

        /**
         * Rapikan teks dan potong di akhir kalimat kalau melebihi [MAX_TEXT],
         * supaya tidak ditolak server dan tidak berhenti di tengah kata.
         */
        fun prepareText(raw: String): String {
            val text = raw.replace(Regex("\\s+"), " ").trim()
            if (text.length <= MAX_TEXT) return text
            val cut = text.substring(0, MAX_TEXT)
            val sentenceEnd = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            if (sentenceEnd >= MAX_TEXT / 2) return cut.substring(0, sentenceEnd + 1)
            val space = cut.lastIndexOf(' ')
            return (if (space > 0) cut.substring(0, space) else cut).trimEnd() + "…"
        }

        internal fun cacheName(text: String, voice: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$voice\n$text".toByteArray())
            return digest.joinToString("") { "%02x".format(it) } + ".mp3"
        }
    }
}
