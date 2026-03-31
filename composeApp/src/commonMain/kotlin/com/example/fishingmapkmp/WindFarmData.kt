package com.example.fishingmapkmp


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import com.russhwolf.settings.get
import kotlinx.serialization.encodeToString

@Serializable
data class CustomMarker(
    val latitude: Double,
    val longitude: Double,
    var name: String
)

@Serializable
data class WindFarmGeoJson(
    val type: String,
    val features: List<WindFarmFeature>
)

@Serializable
data class WindFarmFeature(
    val type: String,
    val properties: WindFarmProperties,
    val geometry: WindFarmGeometry
)

@Serializable
data class WindFarmProperties(
    // 這裡對應 API 裡的風場名稱與詳細資訊
    @SerialName("wpname") val wpName: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("area") val area: Double? = null
)

@Serializable
data class WindFarmGeometry(
    val type: String,
    // GeoJSON 的 Polygon 座標通常是 List<List<List<Double>>>
    // 第一層是 Polygons, 第二層是 Ring (外圈/內圈), 第三層是 [lng, lat]
    val coordinates: List<List<List<Double>>>
)

object MarkerStorage {
    private val json = Json { ignoreUnknownKeys = true }
    // 這裡直接調用無參數建構子 (對應 Android 的 SharedPreferences)
    private val settings: Settings = Settings()

    fun saveMarkers(list: List<CustomMarker>) {
        val serialized = json.encodeToString(list)
        settings["saved_markers"] = serialized
    }

    fun loadMarkers(): List<CustomMarker> {
        val serialized: String = settings.get("saved_markers") ?: ""
        if (serialized.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<CustomMarker>>(serialized)
        } catch (e: Exception) {
            emptyList()
        }
    }
}