package com.example.fishingmapkmp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 1. Data Models (資料結構)
@Serializable
data class JobApiResponse(
    val status: String,
    val data: List<JobItem>
)

@Serializable
data class JobItem(
    @SerialName("ID")
    val id: Int,
    val company: String,
    val title: String,
    val location: String
)

@Serializable
data class CreateJobRequest(
    val company: String,
    val title: String,
    val location: String
)

// 2. Ktor Client (網路請求邏輯)
class JobApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // 忽略 JSON 中未定義的額外欄位，避免解析報錯
                isLenient = true
            })
        }
    }

    private val baseUrl = "https://go-backend-demo.onrender.com/api/v1/jobs"

    // GET: 取得雲端職缺列表
    suspend fun fetchJobs(): JobApiResponse {
        return client.get(baseUrl).body()
    }

    // POST: 新增職缺至雲端
    suspend fun createJob(company: String, title: String, location: String): Boolean {
        val response = client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            header("X-API-Token", "secret123")
            setBody(CreateJobRequest(company, title, location))
        }
        return response.status.value == 200 || response.status.value == 201
    }
}