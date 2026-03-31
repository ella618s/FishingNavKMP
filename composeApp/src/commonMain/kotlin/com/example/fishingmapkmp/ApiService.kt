package com.example.fishingmapkmp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object ApiClient {
    val client = HttpClient {
        // 配置 Json 解析
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // 🚀 這很重要！API 裡很多欄位我們沒用到，設為 true 才不會報錯
            })
        }
    }

    // 抓取風場 API
    suspend fun fetchWindFarmZones(): WindFarmGeoJson {
        // 使用你提供的官網 API 地址
        return client.get("https://windpower.geologycloud.tw/data/Economy/wpzone_approved?f=geojson").body()
    }
}