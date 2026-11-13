package com.cahyo.batur.agent.executor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.cahyo.batur.data.repository.NoteRepository
import com.google.gson.JsonObject

/**
 * Satu executor untuk satu fungsi skill (function_name di tabel skills).
 * [prepare] memvalidasi argumen dari model dan menyusun aksi yang siap dikonfirmasi;
 * aksinya sendiri baru jalan saat [PreparedAction.execute] dipanggil.
 */
interface SkillExecutor {
    val functionName: String

    /** Dipakai kalau data skill dari API tidak bisa dibaca. */
    val defaultConfirmation: Boolean get() = true

    /** @throws InvalidArgumentsException kalau argumen wajib tidak ada atau di luar batas. */
    fun prepare(args: JsonObject): PreparedAction
}

class InvalidArgumentsException(message: String) : IllegalArgumentException(message)

/** Dependensi yang dibutuhkan executor saat aksi dijalankan. */
class ExecContext(
    val context: Context,
    val noteRepository: NoteRepository,
)

data class ExecResult(
    val success: Boolean,
    val message: String,
    /** Mekanisme yang benar-benar dipakai, untuk jejak di Riwayat. */
    val mechanism: String,
)

/** Ringkasan aksi untuk kartu konfirmasi + cara menjalankannya. */
class PreparedAction(
    val functionName: String,
    /** Kata kerja singkat, misalnya "Pasang alarm". */
    val title: String,
    /** Baris label → nilai untuk kartu konfirmasi. */
    val details: List<Pair<String, String>>,
    /** Kunci ikon sesuai kolom `icon` di tabel skills. */
    val icon: String,
    /** Mekanisme Android yang akan dipakai, ditampilkan di kartu. */
    val mechanism: String,
    private val action: suspend (ExecContext) -> ExecResult,
) {
    suspend fun execute(ctx: ExecContext): ExecResult = action(ctx)
}

object SkillExecutors {
    private val all: Map<String, SkillExecutor> = listOf(
        AlarmExecutor,
        TimerExecutor,
        CalendarExecutor,
        NoteExecutor,
        WhatsAppExecutor,
        MapsExecutor,
    ).associateBy { it.functionName }

    fun find(functionName: String): SkillExecutor? = all[functionName]
}

// ---------- Helper argumen ----------

internal fun JsonObject.optString(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonObject.optInt(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.toInt() }.getOrNull() }

internal fun JsonObject.optBoolean(key: String): Boolean? {
    val value = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
    return if (value.isBoolean) value.asBoolean else value.asString.lowercase() in setOf("true", "ya", "1")
}

internal fun JsonObject.requireString(key: String): String =
    optString(key) ?: throw InvalidArgumentsException("$key belum diisi")

internal fun JsonObject.requireInt(key: String, range: IntRange): Int {
    val value = optInt(key) ?: throw InvalidArgumentsException("$key belum diisi")
    if (value !in range) throw InvalidArgumentsException("$key=$value di luar batas ${range.first}–${range.last}")
    return value
}

/** Membuka activity lain; false kalau tidak ada aplikasi yang bisa menanganinya. */
internal fun Context.launch(intent: Intent): Boolean = try {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
