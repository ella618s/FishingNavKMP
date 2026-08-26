package com.example.fishingmapkmp

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==========================================
// 1. Data Models
// ==========================================

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

// 🌊 海象 Data Models
@Serializable
data class SeaConditionResponse(
    val status: String,
    val data: List<SeaCondition>
)

@Serializable
data class SeaCondition(
    @SerialName("location_name") val locationName: String,
    @SerialName("wave_height_m") val waveHeightM: String,
    @SerialName("wind_speed_kts") val windSpeedKts: String,
    @SerialName("tide_info") val tideInfo: String,
    @SerialName("updated_at") val updatedAt: String
)

// 📍 社群漁標 Data Models
@Serializable
data class CommunitySpotResponse(
    val status: String,
    val data: List<CommunitySpot>
)

@Serializable
data class CommunitySpot(
    val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("fish_type") val fishType: String,
    @SerialName("depth_meters") val depthMeters: Double,
    @SerialName("created_by") val createdBy: String
)

// ==========================================
// 2. Ktor Client (整合後版本)
// ==========================================

class JobApiClient {
    // 共用 ApiClient 已經設定好的 HttpClient
    private val client = ApiClient.client

    // 🎯 正確的 Base URL（只到 /api/v1）
    private val baseUrl = "https://go-backend-demo.onrender.com/api/v1"

    // 💼 職缺列表
    suspend fun fetchJobs(): JobApiResponse {
        return client.get("$baseUrl/jobs").body()
    }

    // 💼 新增職缺
    suspend fun createJob(company: String, title: String, location: String): Boolean {
        val response = client.post("$baseUrl/jobs") {
            contentType(ContentType.Application.Json)
            header("X-API-Token", "secret123")
            setBody(CreateJobRequest(company, title, location))
        }
        return response.status.value == 200 || response.status.value == 201
    }

    // 🌊 海象資料 (31 個測站)
    suspend fun fetchSeaConditions(): List<SeaCondition> {
        val response: SeaConditionResponse = client.get("$baseUrl/sea-conditions").body()
        return response.data
    }

    // 📍 社群漁場資料
    suspend fun fetchCommunitySpots(): List<CommunitySpot> {
        val response: CommunitySpotResponse = client.get("$baseUrl/community-spots").body()
        return response.data
    }
}