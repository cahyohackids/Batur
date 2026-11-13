package com.cahyo.batur.data.repository

import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.ApiClient
import com.cahyo.batur.data.remote.ApiException
import com.cahyo.batur.data.remote.ApiResponse
import com.cahyo.batur.data.remote.BaturApi
import com.cahyo.batur.data.remote.apiCall
import com.cahyo.batur.data.remote.bytesCall
import okhttp3.ResponseBody
import retrofit2.Response
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Identitas perangkat di Batur API. Perangkat didaftarkan sekali (lazily, sebelum panggilan
 * pertama yang butuh token); token disimpan di DataStore dan dipasang interceptor [ApiClient].
 */
class DeviceRepository(
    private val client: ApiClient,
    private val settings: SettingsStore,
) {
    // Beberapa layar bisa memanggil API bersamaan saat token belum ada; cukup satu registrasi.
    private val mutex = Mutex()

    suspend fun ensureToken(): Result<String> = mutex.withLock {
        settings.deviceToken()?.let {
            client.token = it
            return@withLock Result.success(it)
        }
        register()
    }

    /** Daftar ulang kalau token [stale] ditolak, kecuali pemanggil lain sudah menggantinya. */
    private suspend fun reRegister(stale: String): Result<String> = mutex.withLock {
        settings.deviceToken()?.takeIf { it != stale }?.let {
            client.token = it
            return@withLock Result.success(it)
        }
        register()
    }

    private suspend fun register(): Result<String> =
        apiCall { client.api().registerDevice() }.map { device ->
            settings.setDevice(device.deviceId, device.token)
            client.token = device.token
            device.token
        }

    /** Panggilan yang wajib token. Kalau server membalas 401, daftar ulang lalu ulangi sekali. */
    suspend fun <T> authed(block: suspend BaturApi.() -> ApiResponse<T>): Result<T> =
        withTokenRetry { apiCall { client.api().block() } }

    /** Seperti [authed], untuk endpoint yang membalas berkas (audio suara). */
    suspend fun authedBytes(block: suspend BaturApi.() -> Response<ResponseBody>): Result<ByteArray> =
        withTokenRetry { bytesCall { client.api().block() } }

    private suspend fun <T> withTokenRetry(call: suspend () -> Result<T>): Result<T> {
        val token = ensureToken().getOrElse { return Result.failure(it) }
        val first = call()
        if (!first.isUnauthorized()) return first
        reRegister(token).getOrElse { return Result.failure(it) }
        return call()
    }

    /**
     * Panggilan yang tetap jalan tanpa token (daftar skill). Token dikirim kalau ada supaya
     * status aktif dan statistik sesuai perangkat ini.
     */
    suspend fun <T> optional(block: suspend BaturApi.() -> ApiResponse<T>): Result<T> {
        val token = ensureToken().getOrNull()
        val first = apiCall { client.api().block() }
        if (!first.isUnauthorized() || token == null) return first
        reRegister(token).getOrElse { return first }
        return apiCall { client.api().block() }
    }

    private fun Result<*>.isUnauthorized(): Boolean = (exceptionOrNull() as? ApiException)?.unauthorized == true
}
