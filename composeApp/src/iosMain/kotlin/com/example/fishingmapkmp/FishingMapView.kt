package com.example.fishingmapkmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FishingMapView(
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
    anomalyStatus: String,// 🎯 【精確新增這行參數，預設為正常航行】
    planRoute: (Double, Double, Double, Double, String) -> List<Pair<Double, Double>> // 🎯 傳入 KMP 智慧路徑規劃方法
) {
    val mapView = remember {
        MKMapView().apply {
            setUserInteractionEnabled(true)
            setZoomEnabled(true)
            setScrollEnabled(true)
            setShowsUserLocation(true) // 顯示藍點
            // 🎯 這裡新增一行：開啟方向光束 (Heading Beam)
            setUserTrackingMode(platform.MapKit.MKUserTrackingModeFollowWithHeading, animated = true)
        }
    }

    UIKitView(
        factory = {
            mapView.apply {
                setUserInteractionEnabled(true)
                setScrollEnabled(true)
                setZoomEnabled(true)
                setRotateEnabled(true)
                // 這裡加入一個物理設定：強迫它成為第一響應者
                becomeFirstResponder()
            }
        },
        modifier = modifier,
        interactive = true,
        update = { view ->
            if (view.userTrackingMode != platform.MapKit.MKUserTrackingModeFollowWithHeading) {
                view.setUserTrackingMode(platform.MapKit.MKUserTrackingModeFollowWithHeading, animated = true)
            }
            val center = CLLocationCoordinate2DMake(initialCenter.first, initialCenter.second)
            val region = MKCoordinateRegionMake(center, MKCoordinateSpanMake(0.05, 0.05))
            view.setRegion(region, animated = false)
        }
    )
}