package com.cahyo.batur.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BaturApi {

    @GET("api/health")
    suspend fun health(): ApiResponse<HealthStatus>

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
        @Query("device_id") deviceId: String,
        @Query("status") status: String? = null,
        @Query("mode") mode: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ApiResponse<List<TaskSummary>>

    @GET("api/tasks/stats")
    suspend fun getTaskStats(@Query("device_id") deviceId: String): ApiResponse<TaskStats>

    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): ApiResponse<TaskDetail>

    @POST("api/tasks")
    suspend fun createTask(@Body body: CreateTaskRequest): ApiResponse<CreatedTask>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int): ApiResponse<DeleteResult>

    @GET("api/notes")
    suspend fun getNotes(@Query("device_id") deviceId: String): ApiResponse<List<Note>>

    @POST("api/notes")
    suspend fun createNote(@Body body: CreateNoteRequest): ApiResponse<Note>

    @POST("api/agent/cloud")
    suspend fun runCloudAgent(@Body body: AgentRequest): ApiResponse<AgentResult>
}
