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

// 🎯 修改建構子：注入 AnomalyDetector 偵測器
class SharedViewModel(private val detector: AnomalyDetector) : ViewModel() {
    // 初始化 Settings 與 Json 處理器
    private val settings: Settings = Settings()
    private val json = Json { ignoreUnknownKeys = true }
    private val STORAGE_KEY = "saved_fishing_spots"
    private val _windFarmData = MutableStateFlow<WindFarmGeoJson?>(null)
    val windFarmData: StateFlow<WindFarmGeoJson?> = _windFarmData

    // 🎯 初始化 AI 需要的滑動視窗緩衝器（每2秒一筆，15筆約30秒）
    private val windowBuffer = SlidingWindowBuffer()

    // 初始化時直接從儲存空間讀取舊資料
    private val _markerList = MutableStateFlow<List<FishingSpot>>(loadSavedSpots())
    val markerList: StateFlow<List<FishingSpot>> = _markerList
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress
    // 宣告一個 StateFlow 讓 iOS 和 Android 畫面能即時知道目前是陸地還是海上導航
    private val _currentMode = MutableStateFlow<NavigationMode>(NavigationMode.SEA) // 預設為海上
    val currentMode: StateFlow<NavigationMode> = _currentMode.asStateFlow()
    // 🎯 儲存當前的航速（單位：公尺/秒，預設 0.0）與航向（單位：度，預設 0.0）
    private val _currentSpeed = MutableStateFlow(0.0)
    private val _currentHeading = MutableStateFlow(0.0)

    // 🎯 智慧防撞警報水管：如果預測有危險會灌入警告訊息，安全則為 null
    private val _collisionAlert = MutableStateFlow<String?>(null)
    val collisionAlert: StateFlow<String?> = _collisionAlert.asStateFlow()

    // 🎯 新增 AI 航行異常狀態水管
    private val _anomalyStatus = MutableStateFlow<String>("正常航行")
    val anomalyStatus: StateFlow<String> = _anomalyStatus.asStateFlow()

    // 🎯 提供給 iOS 監聽警報的水管橋樑
    fun watchCollisionAlert(onUpdate: (String?) -> Unit) {
        viewModelScope.launch {
            collisionAlert.collect { alert ->
                onUpdate(alert)
            }
        }
    }

    // 🎯  提供給 iOS 監聽 AI 異常狀態的水管接頭
    fun watchAnomalyStatus(onUpdate: (String) -> Unit) {
        anomalyStatus.onEach { status ->
            onUpdate(status)
        }.launchIn(viewModelScope)
    }

    /**
     * 提供外部（iOS/Android）主動更新航速與航向的市場介面
     */
    fun updateShipStatus(speedMps: Double, headingDegrees: Double, lat: Double = 0.0, lng: Double = 0.0) {
        _currentSpeed.value = speedMps
        _currentHeading.value = headingDegrees

        // 🎯 每次定位更新時，同步塞入 AI 滑動視窗進行時序檢測
        val snapshot = NavSnapshot(
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            speedKnots = speedMps * 1.94384, // 將 m/s 轉換為航海常用的節 (Knots)
            courseDegrees = headingDegrees,
            latitude = lat,
            longitude = lng
        )

        val windowData = windowBuffer.addSample(snapshot)

        // 當集滿 30 秒數據，立刻啟動 On-Device AI 離線推理
        if (windowData != null) {
            val features = prepareFeatures(windowData)
            val anomalyScore = detector.detectAnomaly(features)

            if (anomalyScore > 0.85f) {
                _anomalyStatus.value = "⚠️ 偵測到航行動態異常（可能遭逢異常洋流、螺旋槳纏繞或失控）"
            } else {
                _anomalyStatus.value = "正常航行"
            }
        }
    }

    /**
     * 🎯 新增 AI 特徵工程：將 15 筆時序快照轉換為模型輸入的特徵向量
     */
    private fun prepareFeatures(snapshots: List<NavSnapshot>): FloatArray {
        val features = FloatArray(30)
        for (i in snapshots.indices) {
            features[i * 2] = snapshots[i].speedKnots.toFloat()
            features[i * 2 + 1] = snapshots[i].courseDegrees.toFloat()
        }
        return features
    }

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
        // 清空記憶體列表 (這會讓兩端的畫面大頭針立刻消失)
        _markerList.value = emptyList()
        // 針對 iOS 的存檔邏輯
        saveAllSpotsToSettings(emptyList())
        // ✅ 針對 Android 的存檔邏輯 (使用 MarkerStorage)
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
     * 智慧全地形路徑規劃 (Android 端使用)
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

        // =========================================================================
        // 🎯 🚀 【Android 同步啟動：智慧地理圍欄防撞預測邏輯】 🚀
        // =========================================================================
        val currentLatLng = GisGeometryUtils.LatLng(currentLat, currentLng)

        // 預測未來 3 分鐘 (180 秒) 的船隻行進軌跡線段
        val predictedLatLng = GisGeometryUtils.predictFutureLocation(
            current = currentLatLng,
            speedMps = _currentSpeed.value,
            headingDegrees = _currentHeading.value,
            durationSeconds = 180.0
        )

        var hasCollisionRisk = false
        var dangerousWindFarmName = ""

        // 遍歷風場多邊形邊界
        _windFarmData.value?.features?.forEach { feature ->
            val name = feature.properties?.wpName ?: "未名風場"
            val polygonRings = feature.geometry?.coordinates
            if (polygonRings != null && polygonRings.isNotEmpty()) {
                val ringPoints = polygonRings[0]

                if (ringPoints.size > 1) {
                    for (i in 0 until ringPoints.size - 1) {
                        val pt1 = ringPoints[i]
                        val pt2 = ringPoints[i + 1]

                        if (pt1.size >= 2 && pt2.size >= 2) {
                            val C = GisGeometryUtils.LatLng(pt1[1], pt1[0])
                            val D = GisGeometryUtils.LatLng(pt2[1], pt2[0])

                            if (GisGeometryUtils.isSegmentsIntersect(currentLatLng, predictedLatLng, C, D)) {
                                hasCollisionRisk = true
                                dangerousWindFarmName = name
                                break
                            }
                        }
                    }
                }
            }
        }

        // 即時灌入水管（Android Compose 畫面可以直接監聽 collisionAlert）
        if (hasCollisionRisk) {
            _collisionAlert.value = "⚠️ 碰撞危機！預計 3 分鐘內將穿越 [${dangerousWindFarmName}] 邊界，請即刻修正航向！"
        } else {
            _collisionAlert.value = null
        }
        // =========================================================================

        return when (mode) {
            NavigationMode.SEA -> {
                println("🌊 AI 偵測：目前處於海域，啟用大圓直線導航")
                listOf(Pair(currentLat, currentLng), Pair(targetLat, targetLng))
            }
            NavigationMode.LAND -> {
                println("🚗 AI 偵測：目前處於陸地，啟用路網導航架構")
                listOf(Pair(currentLat, currentLng), Pair(targetLat, targetLng))
            }
        }
    }

    // 🎯 【關鍵：原生 iOS 水管接頭】
    fun watchCurrentMode(onUpdate: (String) -> Unit) {
        currentMode.onEach { mode ->
            onUpdate(mode.toString())
        }.launchIn(viewModelScope)
    }

    fun planSmartRouteForIOS(
        currentLat: Double,
        currentLng: Double,
        targetLat: Double,
        targetLng: Double,
        targetName: String
    ): String {
        // 執行原本的核心路徑演算法
        val points = planSmartRoute(currentLat, currentLng, targetLat, targetLng, targetName)
        println("=== 🤖 Kotlin 偵測：當前路徑計算完成，總點數為 = ${points.size} ===")

        if (points.isNotEmpty()) {
            _currentMode.value = NavigationMode.LAND
        } else {
            _currentMode.value = NavigationMode.SEA
        }

        // =========================================================================
        // 🎯 🚀 【核心擴充：智慧地理圍欄防撞預測邏輯】 🚀
        // =========================================================================
        val currentLatLng = GisGeometryUtils.LatLng(currentLat, currentLng)

        // 預測未來 3 分鐘 (180 秒) 的船隻行進軌跡線段 (當前位置 -> 預測位置)
        val predictedLatLng = GisGeometryUtils.predictFutureLocation(
            current = currentLatLng,
            speedMps = _currentSpeed.value,
            headingDegrees = _currentHeading.value,
            durationSeconds = 180.0
        )

        var hasCollisionRisk = false
        var dangerousWindFarmName = ""

        // 開始遍歷 GeoJSON 風場資料
        _windFarmData.value?.features?.forEach { feature ->
            val name = feature.properties?.wpName ?: "未名風場"

            val polygonRings = feature.geometry?.coordinates
            if (polygonRings != null && polygonRings.isNotEmpty()) {
                val ringPoints = polygonRings[0]

                if (ringPoints.size > 1) {
                    for (i in 0 until ringPoints.size - 1) {
                        val pt1 = ringPoints[i]
                        val pt2 = ringPoints[i + 1]

                        if (pt1.size >= 2 && pt2.size >= 2) {
                            val C = GisGeometryUtils.LatLng(pt1[1], pt1[0])
                            val D = GisGeometryUtils.LatLng(pt2[1], pt2[0])

                            if (GisGeometryUtils.isSegmentsIntersect(currentLatLng, predictedLatLng, C, D)) {
                                hasCollisionRisk = true
                                dangerousWindFarmName = name
                                break
                            }
                        }
                    }
                }
            }
        }

        // 根據運算結果，即時把警報灌入水管通知 iOS UI
        if (hasCollisionRisk) {
            _collisionAlert.value = "⚠️ 碰撞危機！預計 3 分鐘內將穿越 [${dangerousWindFarmName}] 邊界，請即刻修正航向！"
        } else {
            _collisionAlert.value = null
        }
        // =========================================================================

        // 回傳最純淨的經緯度
        return points.joinToString(separator = ";") { "${it.first},${it.second}" }
    }
}