package com.cahyo.batur.agent

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.core.content.edit
import com.cahyo.batur.data.prefs.ActiveModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/** Status file model .litertlm di ponsel ini. */
sealed interface LocalModelState {
    data object NotDownloaded : LocalModelState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long?,
        /** Alasan tertunda dari DownloadManager, misalnya menunggu jaringan. */
        val waitingReason: String? = null,
    ) : LocalModelState {
        val progress: Float? get() = totalBytes?.takeIf { it > 0 }?.let { downloadedBytes.toFloat() / it }
    }

    data class Ready(val file: File) : LocalModelState

    /**
     * @param fromDownload true kalau DownloadManager melaporkan gagal: status ini bertahan sampai
     * pengguna mencoba lagi atau menutupnya. false untuk file yang ukurannya tidak cocok.
     */
    data class Failed(val message: String, val fromDownload: Boolean = false) : LocalModelState
}

/**
 * Mengunduh dan mengelola file model on-device.
 *
 * Unduhan memakai DownloadManager bawaan Android: tetap jalan di latar belakang, bisa
 * melanjutkan sendiri saat jaringan putus, dan menulis ke folder khusus aplikasi
 * (getExternalFilesDir) sehingga tidak perlu izin penyimpanan.
 */
class ModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val prefs = appContext.getSharedPreferences("model_downloads", Context.MODE_PRIVATE)

    /** Data model yang pernah diunduh, supaya bagian "Di ponsel ini" tetap tampil saat server mati. */
    private val meta = appContext.getSharedPreferences("model_meta", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val modelsDir: File
        get() = (appContext.getExternalFilesDir(DIR) ?: File(appContext.filesDir, DIR)).apply { mkdirs() }

    fun fileFor(fileName: String): File = File(modelsDir, fileName)

    fun freeBytes(): Long = StatFs(modelsDir.path).availableBytes

    /** File dianggap siap kalau unduhan selesai dan ukurannya cocok dengan data dari API. Panggil di luar main thread. */
    fun readyFile(fileName: String, expectedBytes: Long?): File? =
        (currentStateOf(fileName, expectedBytes) as? LocalModelState.Ready)?.file

    /** Pantau status unduhan; berhenti sendiri saat kolektor berhenti. */
    fun observe(fileName: String, expectedBytes: Long?): Flow<LocalModelState> = flow {
        while (true) {
            emit(currentStateOf(fileName, expectedBytes))
            delay(700)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** @return pesan kesalahan, atau null kalau unduhan berhasil dimulai. */
    fun startDownload(url: String, fileName: String, title: String, expectedBytes: Long?): String? {
        if (downloadId(fileName) != null) return null
        dismissFailure(fileName)
        val needed = (expectedBytes ?: 0L) + SAFETY_MARGIN_BYTES
        if (freeBytes() < needed) {
            return "Ruang kosong tidak cukup. Butuh ${formatBytes(needed)}, tersedia ${formatBytes(freeBytes())}."
        }
        // DownloadManager menambah akhiran "-1" kalau nama file sudah ada.
        fileFor(fileName).delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Model AI on-device untuk Batur")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, DIR, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        return try {
            val id = downloadManager.enqueue(request)
            prefs.edit { putLong(fileName, id) }
            null
        } catch (e: Exception) {
            "Unduhan gagal dimulai: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Batalkan unduhan yang sedang berjalan atau hapus file yang sudah ada. */
    fun delete(fileName: String) {
        downloadId(fileName)?.let { downloadManager.remove(it) }
        prefs.edit {
            remove(fileName)
            remove(FAILED_PREFIX + fileName)
        }
        fileFor(fileName).delete()
    }

    /** Tutup pesan unduhan gagal; status kembali ke "Belum diunduh". */
    fun dismissFailure(fileName: String) {
        prefs.edit { remove(FAILED_PREFIX + fileName) }
    }

    /** Simpan data model yang diunduh. [autoActivate] = aktifkan otomatis setelah selesai. */
    fun remember(model: ActiveModel, autoActivate: Boolean) {
        meta.edit {
            putString(model.slug, gson.toJson(model))
            if (autoActivate) putBoolean(AUTO_PREFIX + model.slug, true) else remove(AUTO_PREFIX + model.slug)
        }
    }

    fun forget(slug: String) {
        meta.edit {
            remove(slug)
            remove(AUTO_PREFIX + slug)
        }
    }

    fun clearAutoActivate(slug: String) {
        meta.edit { remove(AUTO_PREFIX + slug) }
    }

    /** Model yang pernah diunduh di ponsel ini (file bisa saja sudah tidak ada). */
    fun knownModels(): List<ActiveModel> = meta.all.entries
        .filter { !it.key.startsWith(AUTO_PREFIX) }
        .mapNotNull { (it.value as? String)?.let { json -> runCatching { gson.fromJson(json, ActiveModel::class.java) }.getOrNull() } }

    fun autoActivateCandidates(): List<ActiveModel> =
        knownModels().filter { meta.getBoolean(AUTO_PREFIX + it.slug, false) }

    private fun downloadId(fileName: String): Long? =
        prefs.getLong(fileName, -1L).takeIf { it >= 0 }

    /** Status saat ini; panggil di luar main thread. */
    fun currentStateOf(fileName: String, expectedBytes: Long?): LocalModelState {
        val id = downloadId(fileName)
        if (id != null) {
            downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        .takeIf { it > 0 } ?: expectedBytes
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    when (status) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING ->
                            return LocalModelState.Downloading(done, total)

                        DownloadManager.STATUS_PAUSED ->
                            return LocalModelState.Downloading(done, total, pausedReason(reason))

                        DownloadManager.STATUS_SUCCESSFUL -> prefs.edit { remove(fileName) }

                        DownloadManager.STATUS_FAILED -> {
                            // remove() juga menghapus file setengah jadi; pesannya disimpan sampai pengguna bertindak.
                            downloadManager.remove(id)
                            prefs.edit {
                                remove(fileName)
                                putString(FAILED_PREFIX + fileName, ModelRequirements.downloadErrorMessage(reason))
                            }
                        }
                    }
                } else {
                    // Unduhan dihapus dari luar aplikasi (misalnya dari notifikasi).
                    prefs.edit { remove(fileName) }
                }
            }
        }
        prefs.getString(FAILED_PREFIX + fileName, null)?.let { return LocalModelState.Failed(it, fromDownload = true) }
        val file = fileFor(fileName)
        return when {
            !file.isFile -> LocalModelState.NotDownloaded
            expectedBytes != null && file.length() != expectedBytes ->
                LocalModelState.Failed("Ukuran file tidak cocok (${formatBytes(file.length())}). Hapus lalu unduh ulang.")
            else -> LocalModelState.Ready(file)
        }
    }

    private fun pausedReason(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Menunggu jaringan"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Menunggu Wi-Fi"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Mencoba lagi"
        else -> "Dijeda"
    }

    companion object {
        private const val DIR = "models"
        private const val FAILED_PREFIX = "failed:"
        private const val AUTO_PREFIX = "auto:"
        private const val SAFETY_MARGIN_BYTES = 300L * 1024 * 1024

        fun formatBytes(bytes: Long): String {
            val gb = bytes / 1_000_000_000.0
            return if (gb >= 1) String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f GB", gb)
            else "${bytes / 1_000_000} MB"
        }
    }
}
