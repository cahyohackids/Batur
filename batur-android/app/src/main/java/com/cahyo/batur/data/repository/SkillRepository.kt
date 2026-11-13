package com.cahyo.batur.data.repository

import com.cahyo.batur.data.remote.ApiClient
import com.cahyo.batur.data.remote.SkillDetail
import com.cahyo.batur.data.remote.SkillSummary
import com.cahyo.batur.data.remote.SkillToggleRequest
import com.cahyo.batur.data.remote.SkillToggleResult
import com.cahyo.batur.data.remote.ToolDefinition
import com.cahyo.batur.data.remote.apiCall

class SkillRepository(private val client: ApiClient) {

    suspend fun getSkills(category: String? = null): Result<List<SkillSummary>> =
        apiCall { client.api().getSkills(category = category) }

    suspend fun getSkill(slug: String): Result<SkillDetail> =
        apiCall { client.api().getSkill(slug) }

    suspend fun setActive(slug: String, active: Boolean): Result<SkillToggleResult> =
        apiCall { client.api().setSkillActive(slug, SkillToggleRequest(active)) }

    suspend fun getTools(): Result<List<ToolDefinition>> =
        apiCall { client.api().getTools() }
}
