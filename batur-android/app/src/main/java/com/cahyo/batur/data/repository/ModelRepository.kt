package com.cahyo.batur.data.repository

import com.cahyo.batur.data.remote.ApiClient
import com.cahyo.batur.data.remote.ModelDetail
import com.cahyo.batur.data.remote.ModelSummary
import com.cahyo.batur.data.remote.apiCall

class ModelRepository(private val client: ApiClient) {

    suspend fun getModels(): Result<List<ModelSummary>> =
        apiCall { client.api().getModels() }

    suspend fun getModel(slug: String): Result<ModelDetail> =
        apiCall { client.api().getModel(slug) }
}
