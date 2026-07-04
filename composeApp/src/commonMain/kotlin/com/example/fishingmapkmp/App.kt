package com.example.fishingmapkmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState

@Composable
fun App() {
    var markerList by remember { mutableStateOf(MarkerStorage.loadMarkers()) }
    var selectedMarker by remember { mutableStateOf<CustomMarker?>(null) }
    var showBottomInfo by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val viewModel = remember { SharedViewModel() }
    val markers by viewModel.markerList.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()

    // 使用 remember 監控 selectedMarker，當它變為 null 時，distText 也會消失
    val distText = remember(selectedMarker, userLocation) {
        if (selectedMarker != null && userLocation != null) {
            // 這裡傳入：使用者緯度, 使用者經度, 目標緯度, 目標經度
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

    // 🎯 新增：更新名稱的邏輯
    val onUpdateMarkerName: (CustomMarker, String) -> Unit = { marker, newName ->
        val newList = markerList.map {
            if (it.latitude == marker.latitude && it.longitude == marker.longitude) {
                it.copy(name = newName) // 假設你的 CustomMarker 是 data class
            } else it
        }
        MarkerStorage.saveMarkers(markers.map { CustomMarker(it.lat, it.lng, it.name) })
        // 如果正在導航的這個點改名了，同步更新選中的狀態
        if (selectedMarker?.latitude == marker.latitude && selectedMarker?.longitude == marker.longitude) {
            selectedMarker = selectedMarker?.copy(name = newName)
        }
    }

    Box(Modifier.fillMaxSize()) {
        FishingMapView(
            modifier = Modifier.fillMaxSize(),
            initialCenter = Pair(25.0330, 121.5654),
            markerList = markers.map { CustomMarker(it.lat, it.lng, it.name) },
            selectedMarker = selectedMarker, // 🎯 確保這裡有傳進去
            onMapClick = { lat, lng, name ->
                viewModel.saveSpot(lat, lng, name)
                // 為了讓 Android 重開 App 還有資料，同步存入 MarkerStorage
                val updatedList =
                    markers.map { CustomMarker(it.lat, it.lng, it.name) } + CustomMarker(
                        lat,
                        lng,
                        name
                    )
                MarkerStorage.saveMarkers(updatedList)
            },
            onMarkerClick = { marker ->
                selectedMarker = marker
                showBottomInfo = (marker != null)
            },
            // 🎯 3. 接收從地圖元件傳回來的經緯度
            onLocationUpdate = { lat, lon ->
                userLocation = Pair(lat, lon)
            },
            onRenameClick = onUpdateMarkerName, // 🎯 傳入改名回呼

            onClearAllClick = {
                // ✅ 執行清空
                viewModel.clearAllSpots()

                // 清除 Android 本地的導航狀態
                selectedMarker = null
                showBottomInfo = false
            },
            // 🎯 補上第一個新參數：傳入從 ViewModel 收到的目前模式
            currentMode = currentMode,

            // 🎯 補上第二個新參數：實作 AI 智慧路徑規劃的銜接
            planRoute = { myLat, myLng, targetLat, targetLng, targetName ->
                // 🎯 直接把 5 個基礎參數傳給 ViewModel，把舊的 val spot = FishingSpot(...) 刪掉！
                viewModel.planSmartRoute(myLat, myLng, targetLat, targetLng, targetName)
            }
        )

        // 🎯 修正：底部資訊視窗 (白框)
        if (selectedMarker != null && showBottomInfo) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 120.dp),
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

// 🎯 在 App.kt 檔案最下方，確保這是整個專案唯一的 MapButton
@Composable
fun MapButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(text = text, color = androidx.compose.ui.graphics.Color.White)
    }
}