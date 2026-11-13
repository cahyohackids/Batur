package com.cahyo.batur.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cahyo.batur.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "batur_settings")

data class ActiveModel(
    val slug: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long?,
)

class SettingsStore(private val context: Context) {

    // Build release hanya mengizinkan HTTPS. Alamat http:// yang tersimpan dari v1.0
    // (misalnya IP laptop) tidak akan pernah tersambung, jadi kembali ke server bawaan.
    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL]?.takeUnless { !BuildConfig.DEBUG && it.startsWith("http://") } ?: DEFAULT_BASE_URL
    }

    /**
     * Token dari server lama tidak berlaku (dan tidak boleh terkirim) ke server lain,
     * jadi identitas perangkat dilepas saat alamat berubah; nanti daftar ulang otomatis.
     */
    suspend fun setBaseUrl(raw: String) {
        val url = normalizeBaseUrl(raw)
        context.dataStore.edit { prefs ->
            if ((prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL) != url) {
                prefs.remove(KEY_DEVICE_TOKEN)
                prefs.remove(KEY_DEVICE_ID)
            }
            prefs[KEY_BASE_URL] = url
        }
    }

    // ---------- Identitas perangkat (dari POST /api/devices/register) ----------

    /** ID perangkat dari server; null sebelum perangkat terdaftar. */
    val deviceId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID]?.takeIf { prefs[KEY_DEVICE_TOKEN] != null }
    }

    suspend fun deviceToken(): String? = context.dataStore.data.first()[KEY_DEVICE_TOKEN]

    suspend fun setDevice(id: String, token: String) {
        context.dataStore.edit {
            it[KEY_DEVICE_ID] = id
            it[KEY_DEVICE_TOKEN] = token
        }
    }

    // ---------- Model on-device ----------

    /** Model on-device yang dipilih di Detail Model. Disimpan lengkap supaya agent tetap jalan saat offline. */
    val activeModel: Flow<ActiveModel?> = context.dataStore.data.map { prefs ->
        val slug = prefs[KEY_MODEL_SLUG] ?: return@map null
        ActiveModel(
            slug = slug,
            name = prefs[KEY_MODEL_NAME] ?: slug,
            fileName = prefs[KEY_MODEL_FILE] ?: return@map null,
            sizeBytes = prefs[KEY_MODEL_SIZE]?.toLongOrNull(),
        )
    }

    suspend fun setActiveModel(model: ActiveModel?) {
        context.dataStore.edit { prefs ->
            if (model == null) {
                listOf(KEY_MODEL_SLUG, KEY_MODEL_NAME, KEY_MODEL_FILE, KEY_MODEL_SIZE).forEach { prefs.remove(it) }
            } else {
                prefs[KEY_MODEL_SLUG] = model.slug
                prefs[KEY_MODEL_NAME] = model.name
                prefs[KEY_MODEL_FILE] = model.fileName
                model.sizeBytes?.let { prefs[KEY_MODEL_SIZE] = it.toString() } ?: prefs.remove(KEY_MODEL_SIZE)
            }
        }
    }

    /** "auto" = on-device kalau model siap, lalu cloud; "cloud" = selalu cloud. */
    val engineMode: Flow<String> = context.dataStore.data.map { it[KEY_ENGINE] ?: ENGINE_AUTO }

    suspend fun setEngineMode(mode: String) {
        context.dataStore.edit { it[KEY_ENGINE] = mode }
    }

    // ---------- Suara ----------

    /** "perempuan" / "laki-laki" = suara cloud lewat server; "ponsel" = TTS bawaan Android saja. */
    val speechVoice: Flow<String> = context.dataStore.data.map { it[KEY_SPEECH_VOICE] ?: VOICE_FEMALE }

    suspend fun setSpeechVoice(voice: String) {
        context.dataStore.edit { it[KEY_SPEECH_VOICE] = voice }
    }

    /** Bacakan jawaban Batur otomatis begitu muncul di layar Agent. */
    val autoSpeak: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SPEAK] ?: false }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SPEAK] = enabled }
    }

    // ---------- Onboarding ----------

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    // ---------- Cache dan antrean (JSON) ----------

    /** Salinan terakhir GET /api/skills/tools, dipakai model on-device saat server tidak terjangkau. */
    suspend fun cachedTools(): String? = read(KEY_TOOLS_CACHE)

    suspend fun setCachedTools(json: String) = write(KEY_TOOLS_CACHE, json)

    /** Jejak tugas yang belum terkirim karena server tidak terjangkau (JSON array CreateTaskRequest). */
    suspend fun pendingTasks(): String? = read(KEY_PENDING_TASKS)

    suspend fun setPendingTasks(json: String?) = write(KEY_PENDING_TASKS, json)

    /** Catatan yang belum terkirim (JSON array PendingNote). */
    val pendingNotes: Flow<String?> = context.dataStore.data.map { it[KEY_PENDING_NOTES] }

    suspend fun setPendingNotes(json: String?) = write(KEY_PENDING_NOTES, json)

    /** 50 pesan chat terakhir di layar Agent. */
    suspend fun chatHistory(): String? = read(KEY_CHAT_HISTORY)

    suspend fun setChatHistory(json: String?) = write(KEY_CHAT_HISTORY, json)

    private suspend fun read(key: Preferences.Key<String>): String? = context.dataStore.data.first()[key]

    private suspend fun write(key: Preferences.Key<String>, value: String?) {
        context.dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }

    companion object {
        /** Debug: backend di laptop (10.0.2.2 dari emulator). Release: server publik. */
        val DEFAULT_BASE_URL: String = BuildConfig.BASE_URL
        const val ENGINE_AUTO = "auto"
        const val ENGINE_CLOUD = "cloud"
        const val VOICE_FEMALE = "perempuan"
        const val VOICE_MALE = "laki-laki"
        const val VOICE_DEVICE = "ponsel"

        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
        private val KEY_MODEL_SLUG = stringPreferencesKey("active_model_slug")
        private val KEY_MODEL_NAME = stringPreferencesKey("active_model_name")
        private val KEY_MODEL_FILE = stringPreferencesKey("active_model_file")
        private val KEY_MODEL_SIZE = stringPreferencesKey("active_model_size")
        private val KEY_ENGINE = stringPreferencesKey("engine_mode")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_TOOLS_CACHE = stringPreferencesKey("tools_cache")
        private val KEY_PENDING_TASKS = stringPreferencesKey("pending_tasks")
        private val KEY_PENDING_NOTES = stringPreferencesKey("pending_notes")
        private val KEY_CHAT_HISTORY = stringPreferencesKey("chat_history")
        private val KEY_SPEECH_VOICE = stringPreferencesKey("speech_voice")
        private val KEY_AUTO_SPEAK = booleanPreferencesKey("auto_speak")

        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

        /**
         * "batur.example.com" -> "https://batur.example.com/", "192.168.1.5:3000/api" -> "http://192.168.1.5:3000/".
         * Tanpa skema dianggap HTTPS, kecuali alamat lokal (IP LAN, 10.0.2.2, localhost) yang biasanya HTTP.
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            val scheme = SCHEME.find(url)
            url = if (scheme != null) {
                scheme.value.lowercase() + url.substring(scheme.value.length)
            } else {
                val host = url.substringBefore('/').substringBefore(':')
                (if (isLocalHost(host)) "http://" else "https://") + url
            }
            url = url.trimEnd('/').removeSuffix("/api")
            return "$url/"
        }

        /** localhost, *.local, 10.0.2.2, dan IP privat (10/8, 172.16/12, 192.168/16, 127/8). */
        internal fun isLocalHost(host: String): Boolean {
            val h = host.lowercase()
            if (h == "localhost" || h.endsWith(".local")) return true
            val parts = h.split('.').map { it.toIntOrNull() ?: return false }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            val (a, b) = parts
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
        }
    }
}
