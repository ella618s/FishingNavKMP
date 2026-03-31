package com.example.fishingmapkmp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FishingMapView(
    modifier: Modifier,
    initialCenter: Pair<Double, Double>,
    markerList: List<CustomMarker>,
    selectedMarker: CustomMarker?,
    onMapClick: (Double, Double, String) -> Unit,
    onMarkerClick: (CustomMarker?) -> Unit,
    onLocationUpdate: (Double, Double) -> Unit, // 🎯 新增這一行：用來傳回目前 GPS 座標
    onRenameClick: (CustomMarker, String) -> Unit // 🎯 新增參數
)