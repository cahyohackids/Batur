package com.cahyo.batur.data.remote

import com.cahyo.batur.data.prefs.SettingsStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Membuat instance Retrofit sesuai base URL yang tersimpan di pengaturan.
 * Base URL bisa diganti dari halaman Profil (misalnya saat pindah ke hotspot),
 * jadi instance dibuat ulang setiap kali URL berubah.
 */
class ApiClient(private val settings: SettingsStore) {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Agent cloud bisa menunggu gateway sampai 20 detik.
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var cached: Pair<String, BaturApi>? = null

    suspend fun api(): BaturApi {
        val baseUrl = settings.baseUrl.first()
        cached?.let { (url, api) -> if (url == baseUrl) return api }
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BaturApi::class.java)
        cached = baseUrl to api
        return api
    }
}

/** Menjalankan panggilan API dan mengubah error teknis menjadi pesan bahasa Indonesia. */
suspend fun <T> apiCall(block: suspend () -> ApiResponse<T>): Result<T> = try {
    val response = block()
    val data = response.data
    if (response.success && data != null) Result.success(data)
    else Result.failure(ApiException(response.message ?: "Respons server tidak valid"))
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: HttpException) {
    Result.failure(ApiException(serverMessage(e) ?: "Server membalas dengan kode ${e.code()}"))
} catch (e: SocketTimeoutException) {
    Result.failure(ApiException("Server terlalu lama merespons. Coba lagi.", offline = true))
} catch (e: IOException) {
    Result.failure(
        ApiException(
            "Tidak bisa terhubung ke server Batur. Pastikan backend menyala dan alamat server di Profil sudah benar.",
            offline = true,
        )
    )
} catch (e: Exception) {
    Result.failure(ApiException("Terjadi kesalahan: ${e.message ?: e.javaClass.simpleName}"))
}

/** @param offline true kalau server tidak terjangkau sama sekali (bukan ditolak server). */
class ApiException(message: String, val offline: Boolean = false) : Exception(message)

private fun serverMessage(e: HttpException): String? = try {
    e.response()?.errorBody()?.string()?.let {
        Gson().fromJson(it, ApiResponse::class.java)?.message
    }
} catch (_: Exception) {
    null
}
