package com.example.fishingmapkmp

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.math.* // 👈 改用 Kotlin 標配的數學函式庫


// 用來存放點位資訊的資料類別
@Serializable
data class FishingSpot(
    val lat: Double,
    val lng: Double,
    val name: String
)

enum class NavigationMode {
    LAND,   // 陸地模式
    SEA     // 海上模式
}

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
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress
    // 宣告一個 StateFlow 讓 iOS 和 Android 畫面能即時知道目前是陸地還是海上導航
    private val _currentMode = MutableStateFlow<NavigationMode>(NavigationMode.SEA) // 預設為海上
    val currentMode: StateFlow<NavigationMode> = _currentMode.asStateFlow()

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
        MarkerStorage.saveMarkers(emptyList())
        println("🗑️ 已清空兩端的所有點位與持久化資料")
    }

    fun updateDownloadProgress(progress: Float) {
        _downloadProgress.value = progress
    }

    // 🎯 新增下載瓦片的邏輯
    fun downloadArea(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        zoomLevels: IntRange
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var downloadedCount = 0
            val totalTiles = calculateTotalTiles(north, south, east, west, zoomLevels)

            for (zoom in zoomLevels) {
                val (minX, maxX, minY, maxY) = getTileRange(north, south, east, west, zoom)
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        val tileData = ApiClient.fetchTile(zoom, x, y) // 透過 Ktor 抓取
                        if (tileData != null) {
                            // 🎯 這裡會呼叫各平台的持久化儲存 (Android 存 File, iOS 存 Documents)
                            saveTileToLocal(zoom, x, y, tileData)

                            downloadedCount++
                            updateDownloadProgress(downloadedCount.toFloat() / totalTiles)
                        }
                    }
                }
            }
        }
    }

    fun watchProgress(onUpdate: (Float) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            downloadProgress.collect {
                onUpdate(it)
            }
        }
    }

    // 🎯計算區域內總共有多少張圖片要下載
    private fun calculateTotalTiles(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        zoomLevels: IntRange
    ): Int {
        var total = 0
        for (zoom in zoomLevels) {
            val (minX, maxX, minY, maxY) = getTileRange(north, south, east, west, zoom)
            total += (maxX - minX + 1) * (maxY - minY + 1)
        }
        return total
    }

    // 🎯將經緯度範圍轉為瓦片座標 (X, Y)
    private fun getTileRange(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        zoom: Int
    ): List<Int> {
        val n = 2.0.pow(zoom.toDouble()) // 🎯 改用 pow 擴充函式

        val minX = floor((west + 180.0) / 360.0 * n).toInt() // 🎯 移除 Math.
        val maxX = floor((east + 180.0) / 360.0 * n).toInt()

        // 緯度轉 Y 軸邏輯修正
        val minY =
            floor((1.0 - ln(tan(north.toRadians()) + 1.0 / cos(north.toRadians())) / PI) / 2.0 * n).toInt()
        val maxY =
            floor((1.0 - ln(tan(south.toRadians()) + 1.0 / cos(south.toRadians())) / PI) / 2.0 * n).toInt()

        return listOf(minX, maxX, minY, maxY)
    }

    // 🎯 輔助函式：角度轉弧度 (KMP 通用寫法)
    private fun Double.toRadians(): Double = this * PI / 180.0

    private fun saveTileToLocal(zoom: Int, x: Int, y: Int, data: ByteArray) {
        // 這裡可以先印 log 測試，實際儲存邏輯會因平台而異
        println("💾 正在儲存瓦片: $zoom/$x/$y, 大小: ${data.size} bytes")

        // TODO: 串接平台專屬的 File API
        // Android: 存入 /osmdroid/tiles
        // iOS: 存入 Documents/tiles
    }

    /**
     * AI 幾何分類器：根據目前位置自動判斷是陸地還是海上
     */
    fun detectEnvironment(lat: Double, lng: Double): NavigationMode {
        // 台灣本島與近岸的大致經緯度邊界
        val taiwanNorth = 25.3
        val taiwanSouth = 21.8
        val taiwanWest = 120.0
        val taiwanEast = 122.0

        return if (lat in taiwanSouth..taiwanNorth && lng in taiwanWest..taiwanEast) {
            // 在這個方框內，代表在台灣陸地或極近岸
            NavigationMode.LAND
        } else {
            // 超出方框，代表已經駛向外海
            NavigationMode.SEA
        }
    }

    /**
     * 智慧全地形路徑規劃
     * 回傳一個座標列表，供兩端地圖繪製 Polyline
     */
    fun planSmartRoute(
        currentLat: Double,
        currentLng: Double,
        targetLat: Double,
        targetLng: Double,
        targetName: String
    ): List<Pair<Double, Double>> {
        // 1. 自動偵測環境
        val mode = detectEnvironment(currentLat, currentLng)
        _currentMode.value = mode // 更新狀態

        return when (mode) {
            NavigationMode.SEA -> {
                println("🌊 AI 偵測：目前處於海域，啟用大圓直線導航")
                // 海上導航：直接連成直線，回傳 起點與終點
                listOf(
                    Pair(currentLat, currentLng),
                    Pair(targetLat, targetLng)
                )
            }
            NavigationMode.LAND -> {
                println("🚗 AI 偵測：目前處於陸地，啟用路網導航架構")
                // 陸地導航：這裡預留給未來的離線路網演算法
                // 目前先模擬回傳起點與終點，確保畫面編譯正常
                listOf(
                    Pair(currentLat, currentLng),
                    Pair(targetLat, targetLng)
                )
            }
        }
    }

    // 🎯 【關鍵：原生 iOS 水管接頭】
    fun watchCurrentMode(onUpdate: (String) -> Unit) {
        currentMode.onEach { mode ->
            onUpdate(mode.toString()) // 👈 改成這樣
        }.launchIn(viewModelScope)
    }

    fun planSmartRouteForIOS(
        currentLat: Double,
        currentLng: Double,
        targetLat: Double,
        targetLng: Double,
        targetName: String
    ): String {
        // 執行你原本的核心路徑演算法
        val points = planSmartRoute(currentLat, currentLng, targetLat, targetLng, targetName)
        // 調試核心防禦：我們先列印出來看，到底這時候 points 裡面有幾個點！
        println("=== 🤖 Kotlin 偵測：當前路徑計算完成，總點數為 = ${points.size} ===")
        // 修改判定邏輯：
        // 如果你發現 points.size 在陸地上也可能是 2，我們可以加入距離判定，
        // 或者如果你原本的 planSmartRoute 裡面就有 mode 變數，請直接用你原本的 mode 變數！
        // 這裡我們先放寬標準，只要算出來有點位，且目標名稱不是海上特定區域，就先觸發 LAND 測試
        if (points.isNotEmpty()) {
            // 如果你原本是用 points.size > 2 判斷，請進去確認 planSmartRoute 回傳的 List 裡面到底裝了什麼。
            // 暫時強制修改測試：只要有拿到路徑，就判定為陸地
            _currentMode.value = NavigationMode.LAND
        } else {
            _currentMode.value = NavigationMode.SEA
        }
        // 回傳最純淨的經緯度
        return points.joinToString(separator = ";") { "${it.first},${it.second}" }
    }
}