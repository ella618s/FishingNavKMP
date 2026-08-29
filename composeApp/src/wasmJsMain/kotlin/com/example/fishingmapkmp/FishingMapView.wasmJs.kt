package com.example.fishingmapkmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun FishingMapView(
    modifier: Modifier,
    initialCenter: Pair<Double, Double>,
    markerList: List<CustomMarker>,
    selectedMarker: CustomMarker?,
    currentMode: NavigationMode,
    onMapClick: (Double, Double, String) -> Unit,
    onMarkerClick: (CustomMarker?) -> Unit,
    onLocationUpdate: (Double, Double) -> Unit,
    onRenameClick: (CustomMarker, String) -> Unit,
    onClearAllClick: () -> Unit,
    anomalyStatus: String,
    weatherAlert: String,
    onSimulateAnomaly: (Double, Double) -> Unit,
    onSimulateBarometerDrop: () -> Unit,
    onResetAnomaly: (Double, Double) -> Unit,
    planRoute: (Double, Double, Double, Double, String) -> List<Pair<Double, Double>>
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🗺️ Web Map Canvas (Wasm)",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "FishingNav KMP 網路版地圖載入中...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
        }
    }
}