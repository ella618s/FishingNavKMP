package com.example.fishingmapkmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import kotlinx.cinterop.useContents

@OptIn(ExperimentalForeignApi::class)
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
    onSimulateAnomaly: (Double, Double) -> Unit,
    onResetAnomaly: (Double, Double) -> Unit,
    planRoute: (Double, Double, Double, Double, String) -> List<Pair<Double, Double>>
) {
    val mapView = remember {
        MKMapView().apply {
            setUserInteractionEnabled(true)
            setZoomEnabled(true)
            setScrollEnabled(true)
            setShowsUserLocation(true)
            setUserTrackingMode(
                platform.MapKit.MKUserTrackingModeFollowWithHeading,
                animated = true
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 🗺️ 底層：iOS 原生 Apple Map
        UIKitView(
            factory = {
                mapView.apply {
                    setUserInteractionEnabled(true)
                    setScrollEnabled(true)
                    setZoomEnabled(true)
                    setRotateEnabled(true)
                    becomeFirstResponder()
                }
            },
            modifier = Modifier.fillMaxSize(),
            interactive = true,
            update = { view ->
                if (view.userTrackingMode != platform.MapKit.MKUserTrackingModeFollowWithHeading) {
                    view.setUserTrackingMode(
                        platform.MapKit.MKUserTrackingModeFollowWithHeading,
                        animated = true
                    )
                }
                val center = CLLocationCoordinate2DMake(initialCenter.first, initialCenter.second)
                val region = MKCoordinateRegionMake(center, MKCoordinateSpanMake(0.05, 0.05))
                view.setRegion(region, animated = false)

                // 💡 順便連動 iOS 的位置回傳（取地圖中心或使用者位置當預設值傳回 ViewModel）
                view.userLocation?.coordinate?.useContents {
                    onLocationUpdate(latitude, longitude)
                }
            }
        )

        // 🧭 右上角按鈕與 AI 監控組（完全對齊 Android 樣式與修正後的邏輯）
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isWarning = anomalyStatus.contains("⚠️")

            // 🤖 Edge AI 狀態監控面板（iOS 終極強制顯示版）
            Box(
                modifier = Modifier
                    .width(145.dp)
                    .height(145.dp)
                    .background(
                        color = if (isWarning) Color.Red.copy(alpha = 0.25f) else Color.DarkGray.copy(
                            alpha = 0.85f
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                // 上半部：標題與狀態資訊
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "🤖 Edge AI 狀態監控",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        text = if (isWarning) "⚠️ 航行異常" else "🟢 正常航行",
                        color = if (isWarning) Color.Red else Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(2.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(text = "演算法: 馬氏距離 (MD)", color = Color.White, fontSize = 9.sp)
                        Text(text = "時序視窗: 30s 滑動視窗", color = Color.White, fontSize = 9.sp)
                        Text(text = "監控維度: 速度 ✕ 航向率", color = Color.White, fontSize = 9.sp)
                    }
                }

                // 下半部：強制絕對定位釘在最底部的智慧按鈕！
                Button(
                    onClick = {
                        var lat = 25.1
                        var lng = 121.5
                        mapView.userLocation?.coordinate?.useContents {
                            if (latitude != 0.0 && longitude != 0.0) {
                                lat = latitude
                                lng = longitude
                            }
                        }
                        if (isWarning) {
                            onResetAnomaly(lat, lng)
                        } else {
                            onSimulateAnomaly(lat, lng)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWarning) Color(0xFF4CAF50) else Color.Red
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .align(Alignment.BottomCenter), // 🎯 釘在最底部
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isWarning) "✅ 恢復正常" else "💥 模擬遭遇暴流",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            // 🗺️ 地圖功能按鈕組
            MapButton("回到我的位置", Color(0xFF6200EE)) {
                mapView.userLocation?.coordinate?.useContents {
                    if (latitude != 0.0 && longitude != 0.0) {
                        val center = CLLocationCoordinate2DMake(latitude, longitude)
                        val region =
                            MKCoordinateRegionMake(center, MKCoordinateSpanMake(0.01, 0.01))
                        mapView.setRegion(region, animated = true)
                    }
                }
            }

            MapButton("清空所有點位", Color.Gray) {
                onClearAllClick()
            }

            if (selectedMarker != null) {
                MapButton("清除導航", Color.Red) {
                    onMarkerClick(null)
                }
            }
        }
    }
}