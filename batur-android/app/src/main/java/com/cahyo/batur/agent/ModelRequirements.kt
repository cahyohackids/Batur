package com.cahyo.batur.agent

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context

/** Model on-device yang disarankan untuk ponsel ini. */
enum class ModelRecommendation(val slug: String?, val label: String) {
    GEMMA("gemma-4-e2b", "Gemma 4 E2B"),
    QWEN("qwen3-0.6b", "Qwen3 0.6B"),
    CLOUD_ONLY(null, "Cloud saja"),
}

/** Kebutuhan RAM model on-device dan rekomendasi berdasarkan RAM ponsel. */
object ModelRequirements {
    private const val GB = 1_000_000_000L

    /** File model ≥ 1,5 GB dianggap kelas Gemma 4 E2B kalau slug-nya tidak dikenal. */
    private const val LARGE_MODEL_BYTES = 1_500_000_000L

    /** RAM total ponsel dalam byte. */
    fun totalRamBytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info.totalMem
    }

    /** RAM minimum (kelas GB, misalnya 6) untuk model; null kalau bukan model on-device yang dikenal. */
    fun requiredRamGb(slug: String?, sizeBytes: Long?): Int? = when {
        slug == ModelRecommendation.GEMMA.slug -> 6
        slug == ModelRecommendation.QWEN.slug -> 3
        sizeBytes == null -> null
        sizeBytes >= LARGE_MODEL_BYTES -> 6
        else -> 3
    }

    /**
     * totalMem selalu lebih kecil dari angka di spesifikasi karena sebagian dipakai kernel dan GPU
     * (HP "6 GB" melaporkan sekitar 5,5 GB), jadi dibandingkan dengan 90% kelasnya.
     */
    fun meetsRam(totalRamBytes: Long, requiredGb: Int): Boolean = totalRamBytes >= requiredGb * GB * 9 / 10

    fun recommend(totalRamBytes: Long): ModelRecommendation = when {
        meetsRam(totalRamBytes, 6) -> ModelRecommendation.GEMMA
        meetsRam(totalRamBytes, 3) -> ModelRecommendation.QWEN
        else -> ModelRecommendation.CLOUD_ONLY
    }

    /** "5,6 GB" untuk ditampilkan. */
    fun formatRam(bytes: Long): String =
        String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f GB", bytes / GB.toDouble())

    /** Terjemahan COLUMN_REASON dari DownloadManager saat STATUS_FAILED. */
    fun downloadErrorMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE ->
            "Penyimpanan ponsel penuh. Kosongkan ruang lalu coba lagi."
        DownloadManager.ERROR_DEVICE_NOT_FOUND ->
            "Penyimpanan tidak ditemukan. Pastikan memori ponsel bisa diakses lalu coba lagi."
        DownloadManager.ERROR_FILE_ERROR, DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
            "File unduhan tidak bisa ditulis. Hapus lalu unduh ulang."
        DownloadManager.ERROR_CANNOT_RESUME ->
            "Unduhan terputus dan tidak bisa dilanjutkan. Unduh ulang dari awal."
        DownloadManager.ERROR_HTTP_DATA_ERROR ->
            "Koneksi terputus saat mengunduh. Cek jaringan lalu coba lagi."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS, DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
            "Server unduhan membalas dengan tidak wajar. Coba lagi nanti."
        in 400..599 -> "Server unduhan menolak permintaan (HTTP $reason). Coba lagi nanti."
        else -> "Unduhan gagal (kode $reason). Coba lagi."
    }
}
