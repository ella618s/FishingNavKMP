package com.example.fishingmapkmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height

@Composable
fun App(viewModel: SharedViewModel) {
    var markerList by remember { mutableStateOf(MarkerStorage.loadMarkers()) }
    var selectedMarker by remember { mutableStateOf<CustomMarker?>(null) }
    var showBottomInfo by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // 控制「雲端資料對話框」是否顯示的狀態變數
    var showJobsDialog by remember { mutableStateOf(false) }

    val markers by viewModel.markerList.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val collisionAlert by viewModel.collisionAlert.collectAsState()
    val anomalyStatus by viewModel.anomalyStatus.collectAsState()
    val weatherAlert by viewModel.weatherAlert.collectAsState()

    // 訂閱 SharedViewModel 中的 API 狀態 (資料與載入中狀態)
    val cloudJobs by viewModel.jobsList.collectAsState()
    val isLoadingJobs by viewModel.isLoadingJobs.collectAsState()
    // 將海象與漁場資料從 SharedViewModel 訂閱出來
    val seaConditions by viewModel.seaConditions.collectAsState()
    val communitySpots by viewModel.communitySpots.collectAsState()

    // 計算與選定點位的距離
    val distText = remember(selectedMarker, userLocation) {
        if (selectedMarker != null && userLocation != null) {
            LocationUtils.calculateDistance(
                userLocation!!.first,
                userLocation!!.second,
                selectedMarker!!.latitude,
                selectedMarker!!.longitude
            )
        } else {
            "計算中..."
        }
    }

    // 更新名稱邏輯
    val onUpdateMarkerName: (CustomMarker, String) -> Unit = { marker, newName ->
        MarkerStorage.saveMarkers(markers.map { CustomMarker(it.lat, it.lng, it.name) })
        if (selectedMarker?.latitude == marker.latitude && selectedMarker?.longitude == marker.longitude) {
            selectedMarker = selectedMarker?.copy(name = newName)
        }
    }

    Box(Modifier.fillMaxSize()) {
        FishingMapView(
            modifier = Modifier.fillMaxSize(),
            initialCenter = Pair(25.0330, 121.5654),
            markerList = markers.map { CustomMarker(it.lat, it.lng, it.name) },
            selectedMarker = selectedMarker,
            currentMode = currentMode,
            onMapClick = { lat, lng, name ->
                viewModel.saveSpot(lat, lng, name)
                val updatedList = markers.map { CustomMarker(it.lat, it.lng, it.name) } + CustomMarker(lat, lng, name)
                MarkerStorage.saveMarkers(updatedList)
            },
            onMarkerClick = { marker ->
                selectedMarker = marker
                showBottomInfo = (marker != null)
            },
            onLocationUpdate = { lat, lon ->
                userLocation = Pair(lat, lon)
                viewModel.updateLocationAndDetectMode(lat, lon)
            },
            onRenameClick = onUpdateMarkerName,
            onClearAllClick = {
                viewModel.clearAllSpots()
                selectedMarker = null
                showBottomInfo = false
            },
            anomalyStatus = anomalyStatus,
            weatherAlert = weatherAlert,

            // 1. 模擬暴流異常
            onSimulateAnomaly = { lat, lng ->
                viewModel.simulateAnomaly(lat, lng)
                repeat(16) {
                    viewModel.updateShipStatus(
                        speedMps = 23.0,
                        headingDegrees = 180.0,
                        lat = lat,
                        lng = lng
                    )
                }
            },

            // 2. 專屬氣壓驟降的觸發事件
            onSimulateBarometerDrop = {
                viewModel.simulateBarometerDrop()
            },

            // 3. 恢復正常數據
            onResetAnomaly = { lat, lng ->
                viewModel.resetAnomaly(lat, lng)
                repeat(16) {
                    viewModel.updateShipStatus(
                        speedMps = 5.14,
                        headingDegrees = 10.0,
                        lat = lat,
                        lng = lng
                    )
                }
            },

            // 4. 智慧導航規劃
            planRoute = { myLat, myLng, targetLat, targetLng, targetName ->
                viewModel.planSmartRoute(myLat, myLng, targetLat, targetLng, targetName)
            }
        )

        // 🚨 顯示碰撞警報 (保留在 App 層高優先級提示)
        collisionAlert?.let { alertMessage ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp),
                color = Color.Red.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = alertMessage, color = Color.White, modifier = Modifier.padding(16.dp))
            }
        }

        // 測試 API 請求的按鈕
        Button(
            onClick = {
                viewModel.fetchCloudJobs()
                showJobsDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 90.dp), // 留出底欄邊距
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0070F3)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("☁️ 雲端 API", color = Color.White)
        }

        // 彈出視窗，用來呈現 API 回傳結果
        if (showJobsDialog) {
            AlertDialog(
                onDismissRequest = { showJobsDialog = false },
                title = { Text("Render 雲端海象與漁場 (Go Backend)") },
                text = {
                    if (isLoadingJobs) {
                        Text("⏳ 正在連線 Render API 撈取資料...")
                    } else if (seaConditions.isEmpty() && communitySpots.isEmpty()) {
                        Text("⚠️ 無資料或連線失敗，請確認網路與 Server 狀態。")
                    } else {
                        // 🎯 使用 LazyColumn + 指定固定高度，啟用順暢上下滑動
                        LazyColumn(
                            modifier = Modifier.height(350.dp)
                        ) {
                            // 🌊 區塊一：全台海象站 (完整顯示 31 筆)
                            item {
                                Text(
                                    text = "🌊 全台海象站 (${seaConditions.size} 筆)",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            items(seaConditions) { sea ->
                                Text(
                                    text = "📍 ${sea.locationName}: 風速 ${sea.windSpeedKts} kts / 浪高 ${sea.waveHeightM} m",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            // 分隔線
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            }
                            // 📍 區塊二：社群漁場
                            item {
                                Text(
                                    text = "📍 社群漁場 (${communitySpots.size} 筆)",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            items(communitySpots) { spot ->
                                Text(
                                    text = "🎣 ${spot.name} (${spot.fishType}) - 水深 ${spot.depthMeters}m",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showJobsDialog = false }) {
                        Text("關閉")
                    }
                }
            )
        }

        // 底部資訊視窗 (點擊 Marker 時顯示)
        if (selectedMarker != null && showBottomInfo) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 90.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "距離 ${selectedMarker!!.name}：$distText")
                    TextButton(onClick = { showBottomInfo = false }) {
                        Text("關閉")
                    }
                }
            }
        }
    }
}

@Composable
fun MapButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(text = text, color = Color.White)
    }
}