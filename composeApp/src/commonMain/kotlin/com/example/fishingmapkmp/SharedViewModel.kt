package com.example.fishingmapkmp

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString


// 用來存放點位資訊的資料類別
@Serializable
data class FishingSpot(
    val lat: Double,
    val lng: Double,
    val name: String
)

class SharedViewModel : ViewModel() {
    // 初始化 Settings 與 Json 處理器
    private val settings: Settings = Settings()
    private val json = Json { ignoreUnknownKeys = true }
    private val STORAGE_KEY = "saved_fishing_spots"

    private val _windFarmData = MutableStateFlow<WindFarmGeoJson?>(null)
    val windFarmData: StateFlow<WindFarmGeoJson?> = _windFarmData

    // 初始化時直接從儲存空間讀取舊資料
    private val _markerList = MutableStateFlow<List<FishingSpot>>(loadSavedSpots())
    val markerList: StateFlow<List<FishingSpot>> = _markerList

    @Throws(Exception::class)
    suspend fun getWindFarmData(): WindFarmGeoJson {
        val data = ApiClient.fetchWindFarmZones()
        _windFarmData.value = data
        return data
    }

    // 存點位時同步寫入儲存空間
    fun saveSpot(lat: Double, lng: Double, name: String) {
        val updatedList = _markerList.value + FishingSpot(lat, lng, name)
        _markerList.value = updatedList

        // 呼叫抽離出來的存檔邏輯
        saveAllSpotsToSettings(updatedList)
        println("📍 新點位已儲存: $name")
    }

    // 讀取存檔的私有方法
    private fun loadSavedSpots(): List<FishingSpot> {
        val savedJson = settings.getString(STORAGE_KEY, "")
        return if (savedJson.isNotEmpty()) {
            try {
                // ✅ 明確指定解碼型別
                json.decodeFromString<List<FishingSpot>>(savedJson)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun updateSpotName(lat: Double, lng: Double, newName: String) {
        val currentList = _markerList.value.toMutableList()
        // 根據座標找到該點位的索引
        val index = currentList.indexOfFirst { it.lat == lat && it.lng == lng }

        if (index != -1) {
            // 更新名稱
            currentList[index] = currentList[index].copy(name = newName)
            _markerList.value = currentList

            // 呼叫同一個存檔邏輯，確保名稱被永久儲存
            saveAllSpotsToSettings(currentList)
            println("📝 點位名稱已更新並存檔: $newName")
        }
    }

    private fun saveAllSpotsToSettings(list: List<FishingSpot>) {
        try {
            // 將整個列表轉成 JSON 字串
            val jsonString = json.encodeToString(list)
            // 存入持久化標籤
            settings.putString(STORAGE_KEY, jsonString)
            println("💾 系統已完成同步存檔，總計 ${list.size} 筆點位")
        } catch (e: Exception) {
            println("❌ 存檔失敗: ${e.message}")
        }
    }

    fun clearAllSpots() {
        // 1. 清空記憶體列表 (這會讓兩端的畫面大頭針立刻消失)
        _markerList.value = emptyList()

        // 2. 針對 iOS 的存檔邏輯
        saveAllSpotsToSettings(emptyList())

        // ✅ 3. 針對 Android 的存檔邏輯 (使用 MarkerStorage)
        // 這樣 Android 下次重開 App 就不會再讀到舊資料
        MarkerStorage.saveMarkers(emptyList())

        println("🗑️ 已清空兩端的所有點位與持久化資料")
    }
}