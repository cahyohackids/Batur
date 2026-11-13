package com.cahyo.batur.agent

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.core.content.edit
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

    data class Failed(val message: String) : LocalModelState
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

    private val modelsDir: File
        get() = (appContext.getExternalFilesDir(DIR) ?: File(appContext.filesDir, DIR)).apply { mkdirs() }

    fun fileFor(fileName: String): File = File(modelsDir, fileName)

    fun freeBytes(): Long = StatFs(modelsDir.path).availableBytes

    /** File dianggap siap kalau unduhan selesai dan ukurannya cocok dengan data dari API. Panggil di luar main thread. */
    fun readyFile(fileName: String, expectedBytes: Long?): File? =
        (currentState(fileName, expectedBytes) as? LocalModelState.Ready)?.file

    /** Pantau status unduhan; berhenti sendiri saat kolektor berhenti. */
    fun observe(fileName: String, expectedBytes: Long?): Flow<LocalModelState> = flow {
        while (true) {
            emit(currentState(fileName, expectedBytes))
            delay(700)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** @return pesan kesalahan, atau null kalau unduhan berhasil dimulai. */
    fun startDownload(url: String, fileName: String, title: String, expectedBytes: Long?): String? {
        if (downloadId(fileName) != null) return null
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
        prefs.edit { remove(fileName) }
        fileFor(fileName).delete()
    }

    private fun downloadId(fileName: String): Long? =
        prefs.getLong(fileName, -1L).takeIf { it >= 0 }

    private fun currentState(fileName: String, expectedBytes: Long?): LocalModelState {
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
                            delete(fileName)
                            return LocalModelState.Failed("Unduhan gagal (kode $reason). Coba lagi.")
                        }
                    }
                } else {
                    // Unduhan dihapus dari luar aplikasi (misalnya dari notifikasi).
                    prefs.edit { remove(fileName) }
                }
            }
        }
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
        private const val SAFETY_MARGIN_BYTES = 300L * 1024 * 1024

        fun formatBytes(bytes: Long): String {
            val gb = bytes / 1_000_000_000.0
            return if (gb >= 1) String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f GB", gb)
            else "${bytes / 1_000_000} MB"
        }
    }
}
