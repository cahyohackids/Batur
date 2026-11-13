package com.cahyo.batur.data.remote

import com.cahyo.batur.BuildConfig
import com.cahyo.batur.data.prefs.SettingsStore
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Membuat instance Retrofit sesuai base URL yang tersimpan di pengaturan.
 * Base URL bisa diganti dari halaman Profil, jadi instance dibuat ulang setiap kali URL berubah.
 */
class ApiClient(private val settings: SettingsStore) {

    /** Token perangkat dari /api/devices/register. Diisi oleh DeviceRepository. */
    @Volatile var token: String? = null

    private val headers = Interceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder().header("X-App-Version", BuildConfig.VERSION_NAME)
        val current = token
        if (current != null && !request.url().encodedPath().endsWith(BaturApi.REGISTER_PATH)) {
            builder.header("Authorization", "Bearer $current")
        }
        chain.proceed(builder.build())
    }

    private val okHttp = OkHttpClient.Builder()
        // Server yang mati harus cepat ketahuan supaya antrean offline dan fallback tidak lama menunggu.
        .connectTimeout(4, TimeUnit.SECONDS)
        // Agent cloud bisa menunggu gateway sampai 20 detik.
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(headers)
        .build()

    @Volatile private var cached: Pair<String, BaturApi>? = null

    suspend fun api(): BaturApi {
        val baseUrl = settings.baseUrl.first()
        cached?.let { (url, api) -> if (url == baseUrl) return api }
        // Alamat berubah: token lama sudah dihapus SettingsStore, jangan sampai terkirim ke server baru.
        token = settings.deviceToken()
        return apiFor(baseUrl).also { cached = baseUrl to it }
    }

    /** Instance untuk alamat tertentu, misalnya untuk tes koneksi sebelum alamat disimpan. */
    fun apiFor(baseUrl: String): BaturApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BaturApi::class.java)
}

/** Menjalankan panggilan API dan mengubah error teknis menjadi pesan bahasa Indonesia. */
suspend fun <T> apiCall(block: suspend () -> ApiResponse<T>): Result<T> = try {
    val response = block()
    val data = response.data
    when {
        response.success && data != null -> Result.success(data)
        response.success -> Result.failure(ApiException(PARSE_MESSAGE, parseError = true))
        else -> Result.failure(ApiException(response.message ?: "Permintaan ditolak server", code = response.code))
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: HttpException) {
    Result.failure(httpFailure(e))
} catch (e: JsonParseException) {
    // Misalnya halaman HTML dari proxy/Cloudflare, bukan JSON dari Batur API.
    Result.failure(ApiException(PARSE_MESSAGE, parseError = true))
} catch (e: SocketTimeoutException) {
    Result.failure(ApiException("Server terlalu lama merespons. Coba lagi.", offline = true))
} catch (e: IOException) {
    Result.failure(
        ApiException(
            "Tidak bisa terhubung ke server Batur. Cek internet dan alamat server di Profil.",
            offline = true,
        )
    )
} catch (e: Exception) {
    Result.failure(ApiException("Terjadi kesalahan: ${e.message ?: e.javaClass.simpleName}"))
}

/** Seperti [apiCall], untuk endpoint yang membalas berkas (audio), bukan JSON. */
suspend fun bytesCall(block: suspend () -> Response<ResponseBody>): Result<ByteArray> = apiCall {
    val response = block()
    if (!response.isSuccessful) throw HttpException(response)
    val bytes = withContext(Dispatchers.IO) { response.body()?.use { it.bytes() } }
    ApiResponse(success = bytes != null && bytes.isNotEmpty(), data = bytes, message = null, meta = null)
}

private const val PARSE_MESSAGE = "Respons server tidak bisa dibaca. Coba lagi nanti."

/**
 * @param offline true kalau server tidak terjangkau sama sekali (bukan ditolak server).
 * @param httpCode kode HTTP kalau server membalas dengan status error.
 * @param code kode error dari Batur API, misalnya "quota_exceeded".
 * @param retryable true kalau permintaan yang sama layak dikirim ulang nanti (antrean offline).
 */
class ApiException(
    message: String,
    val offline: Boolean = false,
    val httpCode: Int? = null,
    val code: String? = null,
    parseError: Boolean = false,
    val retryable: Boolean = isRetryableFailure(offline, httpCode, parseError),
) : Exception(message) {
    val unauthorized: Boolean get() = httpCode == 401
}

/**
 * Kegagalan yang bukan salah datanya: offline, server sibuk/rusak, dibatasi, atau respons rusak.
 * 401 juga dianggap sementara karena token diperbarui otomatis; 4xx lain berarti data ditolak.
 */
internal fun isRetryableFailure(offline: Boolean, httpCode: Int?, parseError: Boolean): Boolean = when {
    offline || parseError -> true
    httpCode == null -> false
    httpCode >= 500 -> true
    else -> httpCode == 401 || httpCode == 408 || httpCode == 429
}

private val errorGson = Gson()

private fun httpFailure(e: HttpException): ApiException {
    val status = e.code()
    val body = try {
        e.response()?.errorBody()?.string()?.let { errorGson.fromJson(it, ApiResponse::class.java) }
    } catch (_: Exception) {
        null
    }
    val message = body?.message ?: when {
        status == 401 -> "Perangkat belum terdaftar di server."
        status == 429 -> "Terlalu banyak permintaan. Tunggu sebentar lalu coba lagi."
        status >= 500 -> "Server sedang bermasalah (kode $status). Coba lagi nanti."
        else -> "Server membalas dengan kode $status"
    }
    return ApiException(message, httpCode = status, code = body?.code)
}
