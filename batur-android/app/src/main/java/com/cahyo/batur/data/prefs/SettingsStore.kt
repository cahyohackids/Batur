package com.cahyo.batur.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "batur_settings")

data class ActiveModel(
    val slug: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long?,
)

class SettingsStore(private val context: Context) {

    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }

    suspend fun setBaseUrl(raw: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = normalizeBaseUrl(raw) }
    }

    /** UUID yang dibuat sekali saat aplikasi pertama dibuka, dipakai sebagai pemilik riwayat. */
    suspend fun deviceId(): String {
        context.dataStore.data.first()[KEY_DEVICE_ID]?.let { return it }
        var id = ""
        context.dataStore.edit { prefs ->
            id = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[KEY_DEVICE_ID] = it }
        }
        return id
    }

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

    /** Salinan terakhir GET /api/skills/tools, dipakai model on-device saat server tidak terjangkau. */
    suspend fun cachedTools(): String? = context.dataStore.data.first()[KEY_TOOLS_CACHE]

    suspend fun setCachedTools(json: String) {
        context.dataStore.edit { it[KEY_TOOLS_CACHE] = json }
    }

    /** Jejak tugas yang belum terkirim karena server tidak terjangkau (JSON array CreateTaskRequest). */
    suspend fun pendingTasks(): String? = context.dataStore.data.first()[KEY_PENDING_TASKS]

    suspend fun setPendingTasks(json: String?) {
        context.dataStore.edit { if (json == null) it.remove(KEY_PENDING_TASKS) else it[KEY_PENDING_TASKS] = json }
    }

    companion object {
        // 10.0.2.2 = localhost laptop dilihat dari emulator Android.
        const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/"
        const val ENGINE_AUTO = "auto"
        const val ENGINE_CLOUD = "cloud"

        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_MODEL_SLUG = stringPreferencesKey("active_model_slug")
        private val KEY_MODEL_NAME = stringPreferencesKey("active_model_name")
        private val KEY_MODEL_FILE = stringPreferencesKey("active_model_file")
        private val KEY_MODEL_SIZE = stringPreferencesKey("active_model_size")
        private val KEY_ENGINE = stringPreferencesKey("engine_mode")
        private val KEY_TOOLS_CACHE = stringPreferencesKey("tools_cache")
        private val KEY_PENDING_TASKS = stringPreferencesKey("pending_tasks")

        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            url = url.removeSuffix("/api/").removeSuffix("/api")
            return if (url.endsWith("/")) url else "$url/"
        }
    }
}
