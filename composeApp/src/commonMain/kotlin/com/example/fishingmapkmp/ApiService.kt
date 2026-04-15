package com.example.fishingmapkmp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object ApiClient {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
        }
    }

    @Throws(Exception::class)
    suspend fun fetchWindFarmZones(): WindFarmGeoJson = withContext(Dispatchers.Default) {
        val urlString = "https://windpower.geologycloud.tw/data/Economy/wpzone_approved?f=geojson"

        try {
            val response = client.get(urlString) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header(HttpHeaders.Accept, "application/json")
            }

            val body = response.bodyAsText()

            // 🎯 關鍵檢查：如果伺服器亂吐 error，我們就丟出異常，不要讓它進解析
            if (body.contains("error") || body.contains("invalid path")) {
                throw Exception("Server rejected iOS request")
            }

            Json { ignoreUnknownKeys = true }.decodeFromString<WindFarmGeoJson>(body)

        } catch (e: Exception) {
            println("⚠️ 網路抓取失敗，回傳測試點位")

            // 🎯 依照你的 WindFarmGeometry 定義 (Polygon 結構)
            // 座標需要是 List<List<List<Double>>>
            val testCoordinates = listOf(
                listOf(
                    listOf(119.8, 23.4),
                    listOf(120.2, 23.4),
                    listOf(120.2, 23.6),
                    listOf(119.8, 23.6),
                    listOf(119.8, 23.4) // 封閉多邊形
                )
            )

            val testFeature = WindFarmFeature(
                type = "Feature",
                properties = WindFarmProperties(
                    wpName = "測試風場-iOS診斷用",
                    status = "測試中",
                    area = 100.0
                ),
                geometry = WindFarmGeometry(
                    type = "Polygon",
                    coordinates = testCoordinates
                )
            )

            // 這裡回傳 return
            WindFarmGeoJson(
                type = "FeatureCollection",
                features = listOf(testFeature)
            )
        }
    }
}