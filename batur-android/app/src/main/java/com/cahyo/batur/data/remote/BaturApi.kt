package com.cahyo.batur.data.remote

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Header Authorization dipasang interceptor di [ApiClient]; pemilik data dibaca server dari token. */
interface BaturApi {

    @GET("api/health")
    suspend fun health(): ApiResponse<HealthStatus>

    @POST(REGISTER_PATH)
    suspend fun registerDevice(@Body body: JsonObject = JsonObject()): ApiResponse<RegisteredDevice>

    @GET("api/app/version")
    suspend fun appVersion(): ApiResponse<AppVersion>

    @GET("api/skills")
    suspend fun getSkills(
        @Query("category") category: String? = null,
        @Query("active") active: Boolean? = null,
    ): ApiResponse<List<SkillSummary>>

    @GET("api/skills/tools")
    suspend fun getTools(): ApiResponse<List<ToolDefinition>>

    @GET("api/skills/{slug}")
    suspend fun getSkill(@Path("slug") slug: String): ApiResponse<SkillDetail>

    @PATCH("api/skills/{slug}")
    suspend fun setSkillActive(
        @Path("slug") slug: String,
        @Body body: SkillToggleRequest,
    ): ApiResponse<SkillToggleResult>

    @GET("api/models")
    suspend fun getModels(@Query("type") type: String? = null): ApiResponse<List<ModelSummary>>

    @GET("api/models/{slug}")
    suspend fun getModel(@Path("slug") slug: String): ApiResponse<ModelDetail>

    @GET("api/tasks")
    suspend fun getTasks(
        @Query("status") status: String? = null,
        @Query("mode") mode: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiResponse<List<TaskSummary>>

    @GET("api/tasks/stats")
    suspend fun getTaskStats(): ApiResponse<TaskStats>

    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): ApiResponse<TaskDetail>

    @POST("api/tasks")
    suspend fun createTask(@Body body: CreateTaskRequest): ApiResponse<CreatedTask>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int): ApiResponse<DeleteResult>

    @GET("api/notes")
    suspend fun getNotes(): ApiResponse<List<Note>>

    @POST("api/notes")
    suspend fun createNote(@Body body: CreateNoteRequest): ApiResponse<Note>

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") id: Int): ApiResponse<DeleteResult>

    @POST("api/agent/cloud")
    suspend fun runCloudAgent(@Body body: AgentRequest): ApiResponse<AgentResult>

    /** Audio mp3 jawaban Batur. Error tetap berupa JSON ApiResponse. */
    @POST("api/tts")
    suspend fun speech(@Body body: SpeechRequest): Response<ResponseBody>

    companion object {
        const val REGISTER_PATH = "api/devices/register"
    }
}
