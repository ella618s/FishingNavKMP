package com.example.fishingmapkmp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FishingMapView(
    modifier: Modifier,
    initialCenter: Pair<Double, Double>,
    markerList: List<CustomMarker>,
    selectedMarker: CustomMarker?,
    currentMode: NavigationMode, // 🎯 接收來自 ViewModel 的 AI 模式狀態
    onMapClick: (Double, Double, String) -> Unit,
    onMarkerClick: (CustomMarker?) -> Unit,
    onLocationUpdate: (Double, Double) -> Unit, // 🎯 用來傳回目前 GPS 座標
    onRenameClick: (CustomMarker, String) -> Unit, // 🎯 新增參數
    onClearAllClick: () -> Unit,
    anomalyStatus: String = "正常航行",// 🎯 【精確新增這行參數，預設為正常航行】
    onSimulateAnomaly: (Double, Double) -> Unit,
    onResetAnomaly: (Double, Double) -> Unit,
    planRoute: (Double, Double, Double, Double, String) -> List<Pair<Double, Double>> // 🎯 傳入 KMP 智慧路徑規劃方法
)