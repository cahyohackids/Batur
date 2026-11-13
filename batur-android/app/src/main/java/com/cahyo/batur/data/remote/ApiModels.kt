package com.cahyo.batur.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

// Format respons Batur API: { success, data, meta? } atau { success: false, message }
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val meta: PageMeta?,
)

data class PageMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
)

// ---------- Skills ----------

data class SkillSummary(
    val id: Int,
    val slug: String,
    val name: String,
    val description: String,
    val category: String,
    val icon: String,
    @SerializedName("function_name") val functionName: String,
    @SerializedName("requires_confirmation") val requiresConfirmation: Boolean,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("example_count") val exampleCount: Int,
)

data class SkillDetail(
    val id: Int,
    val slug: String,
    val name: String,
    val description: String,
    val category: String,
    val icon: String,
    @SerializedName("function_name") val functionName: String,
    val parameters: JsonObject?,
    @SerializedName("example_commands") val exampleCommands: List<String>?,
    @SerializedName("android_action") val androidAction: String?,
    val permissions: List<String>?,
    @SerializedName("requires_confirmation") val requiresConfirmation: Boolean,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("usage_count") val usageCount: Int,
    @SerializedName("success_count") val successCount: Int,
    @SerializedName("last_used_at") val lastUsedAt: String?,
)

data class SkillToggleRequest(@SerializedName("is_active") val isActive: Boolean)

data class SkillToggleResult(
    val slug: String,
    @SerializedName("is_active") val isActive: Boolean,
)

data class ToolDefinition(
    val type: String,
    val function: ToolFunction,
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject?,
)

// ---------- Models ----------

data class ModelSummary(
    val id: Int,
    val slug: String,
    val name: String,
    val type: String,
    val provider: String,
    @SerializedName("size_label") val sizeLabel: String?,
    @SerializedName("is_default") val isDefault: Boolean,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("size_bytes") val sizeBytes: Long?,
)

data class ModelDetail(
    val id: Int,
    val slug: String,
    val name: String,
    val type: String,
    val provider: String,
    val runtime: String,
    @SerializedName("gateway_model") val gatewayModel: String?,
    @SerializedName("size_label") val sizeLabel: String?,
    @SerializedName("ram_label") val ramLabel: String?,
    @SerializedName("supports_tools") val supportsTools: Boolean,
    val description: String,
    @SerializedName("is_default") val isDefault: Boolean,
    @SerializedName("usage_count") val usageCount: Int,
    @SerializedName("avg_latency_ms") val avgLatencyMs: Int?,
    @SerializedName("download_url") val downloadUrl: String?,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("size_bytes") val sizeBytes: Long?,
)

// ---------- Tasks ----------

data class TaskSummary(
    val id: Int,
    val command: String,
    val mode: String,
    @SerializedName("skill_slug") val skillSlug: String?,
    val status: String,
    @SerializedName("result_text") val resultText: String?,
    @SerializedName("latency_ms") val latencyMs: Int?,
    @SerializedName("created_at") val createdAt: String,
)

data class TaskDetail(
    val id: Int,
    val command: String,
    val mode: String,
    @SerializedName("model_slug") val modelSlug: String?,
    @SerializedName("skill_slug") val skillSlug: String?,
    // JsonElement, bukan JsonObject: Gson gagal membaca `null` ke field JsonObject
    // (riwayat pertanyaan biasa tidak punya function_call).
    @SerializedName("function_call") val functionCall: JsonElement?,
    val status: String,
    @SerializedName("result_text") val resultText: String?,
    @SerializedName("latency_ms") val latencyMs: Int?,
    @SerializedName("created_at") val createdAt: String,
    val steps: List<TaskStep>?,
)

data class TaskStep(
    val type: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String?,
)

data class TaskStats(
    val total: Int,
    val success: Int,
    @SerializedName("on_device") val onDevice: Int,
    val cloud: Int,
    val fallback: Int,
    @SerializedName("avg_latency_ms") val avgLatencyMs: Int?,
    @SerializedName("top_skill") val topSkill: String?,
)

data class FunctionCall(
    val name: String,
    val arguments: JsonObject?,
)

data class StepRequest(val type: String, val content: String)

data class CreateTaskRequest(
    @SerializedName("device_id") val deviceId: String,
    val command: String,
    val mode: String,
    @SerializedName("model_slug") val modelSlug: String?,
    @SerializedName("skill_slug") val skillSlug: String?,
    @SerializedName("function_call") val functionCall: FunctionCall?,
    val status: String,
    @SerializedName("result_text") val resultText: String?,
    @SerializedName("latency_ms") val latencyMs: Int?,
    val steps: List<StepRequest>,
)

data class CreatedTask(val id: Int)

data class DeleteResult(val id: Int, val deleted: Boolean)

// ---------- Notes ----------

data class Note(
    val id: Int,
    val content: String,
    @SerializedName("task_id") val taskId: Int?,
    @SerializedName("created_at") val createdAt: String,
)

data class CreateNoteRequest(
    @SerializedName("device_id") val deviceId: String,
    val content: String,
    @SerializedName("task_id") val taskId: Int?,
)

// ---------- Agent ----------

data class AgentRequest(
    val command: String,
    @SerializedName("device_id") val deviceId: String,
)

data class AgentResult(
    val mode: String,
    val type: String,
    @SerializedName("function_call") val functionCall: FunctionCall?,
    val skill: String?,
    val text: String?,
    @SerializedName("latency_ms") val latencyMs: Int,
)

data class HealthStatus(
    val status: String,
    @SerializedName("db_time") val dbTime: String?,
)
