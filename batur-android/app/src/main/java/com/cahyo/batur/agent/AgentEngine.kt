package com.cahyo.batur.agent

import android.os.SystemClock
import com.cahyo.batur.data.remote.AgentRequest
import com.cahyo.batur.data.remote.FunctionCall
import com.cahyo.batur.data.repository.DeviceRepository

/** Hasil "otak" agent: memilih skill (tool_call) atau menjawab biasa (text). */
data class EngineOutput(
    /** on_device | cloud | fallback */
    val mode: String,
    val modelSlug: String?,
    val type: String,
    val functionCall: FunctionCall?,
    val skillSlug: String?,
    val text: String?,
    /** Waktu dari perintah dikirim sampai keputusan diterima (diukur di ponsel). */
    val latencyMs: Int,
)

interface AgentEngine {
    suspend fun run(command: String): Result<EngineOutput>
}

/** Mode cloud: POST /api/agent/cloud. Server memakai gateway, atau parser aturan kalau gateway mati. */
class CloudAgentEngine(private val device: DeviceRepository) : AgentEngine {

    override suspend fun run(command: String): Result<EngineOutput> {
        val start = SystemClock.elapsedRealtime()
        return device.authed { runCloudAgent(AgentRequest(command)) }.map { r ->
            EngineOutput(
                mode = r.mode,
                modelSlug = if (r.mode == "cloud") CLOUD_MODEL_SLUG else null,
                type = r.type,
                functionCall = r.functionCall,
                skillSlug = r.skill,
                text = r.text,
                latencyMs = (SystemClock.elapsedRealtime() - start).toInt(),
            )
        }
    }

    companion object {
        const val CLOUD_MODEL_SLUG = "cloud-gateway"
    }
}
