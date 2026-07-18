package com.example.fishingmapkmp

import android.graphics.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay // 🎯 導入指南針
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.tileprovider.cachemanager.CacheManager
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background

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
    onRenameClick: (CustomMarker, String) -> Unit,
    onClearAllClick: () -> Unit,
    anomalyStatus: String, // 🎯 【預設為正常航行】
    onSimulateAnomaly: (Double, Double) -> Unit,  // 直接將模擬測試需要的事件用 lambda 傳出去給 ViewModel
    onResetAnomaly: (Double, Double) -> Unit,
    planRoute: (Double, Double, Double, Double, String) -> List<Pair<Double, Double>> // 🎯 傳入 KMP 智慧路徑規劃方法
) {
    val context = LocalContext.current
    var isSatelliteMode by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val windFarmData = remember { mutableStateOf<WindFarmGeoJson?>(null) }
    // --- 🎯 新增點位名稱輸入視窗相關狀態 ---
    var showMarkerDialog by remember { mutableStateOf(false) }
    var tempLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var markerNameInput by remember { mutableStateOf("新釣點") }
    // 建立改名用的狀態
    var showRenameDialog by remember { mutableStateOf(false) }
    var markerToRename by remember { mutableStateOf<CustomMarker?>(null) }
    var newNameInput by remember { mutableStateOf("") }
    var mapReference by remember { mutableStateOf<MapView?>(null) }
    val scope = rememberCoroutineScope()

    // 改名對話框 (UI 層)
    if (showRenameDialog && markerToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("修改釣點名稱") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("新名稱") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newNameInput.isNotBlank()) {
                        onRenameClick(markerToRename!!, newNameInput)
                    }
                    showRenameDialog = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    // 當進入畫面時自動抓取 API
    LaunchedEffect(Unit) {
        try {
            val result = ApiClient.fetchWindFarmZones()
            windFarmData.value = result
        } catch (e: Exception) {
            e.printStackTrace() // 處理網路錯誤
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
                MapView(ctx).apply {
                    mapReference = this
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(initialCenter.first, initialCenter.second))

                    // 🎯 必須在這裡 new，傳入 this 就不會閃退
                    val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)

                    // --- 綠色三角形繪製邏輯 ---
                    val bitSize = 60
                    val bit = Bitmap.createBitmap(bitSize, bitSize, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bit)
                    val p = Paint().apply {
                        color = android.graphics.Color.GREEN
                        style = Paint.Style.FILL
                        isAntiAlias = true
                        setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    val path = Path().apply {
                        moveTo(30f, 5f); lineTo(50f, 55f); lineTo(30f, 45f); lineTo(
                        10f,
                        55f
                    ); close()
                    }
                    canvas.drawPath(path, p)
                    overlay.setDirectionArrow(bit, bit)
                    overlay.setDirectionAnchor(0.5f, 0.5f)
                    overlay.setPersonAnchor(0.5f, 0.5f)
                    overlay.enableMyLocation()
                    overlays.add(overlay)

                    locationOverlayRef = overlay // 存入引用

                    // 指南針
                    val compass = CompassOverlay(ctx, this)
                    compass.enableCompass()
                    overlays.add(compass)

                    // 🎯 地圖點擊監聽
                    val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            p?.let {
                                tempLocation = it; markerNameInput = "新釣點"; showMarkerDialog =
                                true
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }
                    overlays.add(org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver))

                    mapViewRef = this // 保存地圖引用
                }
            },
            update = { mapView ->
                mapReference = mapView
                // 🎯 衛星/一般模式切換
                if (isSatelliteMode) {
                    mapView.setTileSource(object :
                        org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
                            "Google-Satellite", 0, 20, 256, ".jpg",
                            arrayOf("https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}")
                        ) {
                        override fun getTileURLString(pTileIndex: Long): String = baseUrl
                            .replace(
                                "{x}",
                                org.osmdroid.util.MapTileIndex.getX(pTileIndex).toString()
                            )
                            .replace(
                                "{y}",
                                org.osmdroid.util.MapTileIndex.getY(pTileIndex).toString()
                            )
                            .replace(
                                "{z}",
                                org.osmdroid.util.MapTileIndex.getZoom(pTileIndex).toString()
                            )
                    })
                } else {
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                }

                // 🎯 繪製標記與導航線
                mapView.overlays.removeAll { it is Marker || it is Polyline }

                // 如果 API 資料抓到了，就畫在地圖上
                windFarmData.value?.let { data ->
                    data.features.forEach { feature ->
                        val osmdroidPolygon = org.osmdroid.views.overlay.Polygon()
                        val points = mutableListOf<GeoPoint>()

                        feature.geometry.coordinates.firstOrNull()?.forEach { coord ->
                            points.add(GeoPoint(coord[1], coord[0]))
                        }

                        osmdroidPolygon.points = points
                        osmdroidPolygon.fillPaint.color =
                            android.graphics.Color.parseColor("#406200EE") // 半透明紫
                        osmdroidPolygon.strokeColor = android.graphics.Color.parseColor("#FF6200EE")
                        osmdroidPolygon.strokeWidth = 3f
                        osmdroidPolygon.title = feature.properties.wpName ?: "未知風場"

                        mapView.overlays.add(osmdroidPolygon)
                    }
                    mapView.invalidate()
                }

                // 畫大頭針
                markerList.forEach { data ->
                    val m = Marker(mapView).apply {
                        position = GeoPoint(data.latitude, data.longitude)
                        title = data.name
                        infoWindow = null
                        setOnMarkerClickListener { marker, _ ->
                            if (selectedMarker?.latitude == data.latitude &&
                                selectedMarker?.longitude == data.longitude
                            ) {
                                onMarkerClick(data)
                                markerToRename = data
                                newNameInput = data.name
                                showRenameDialog = true
                            } else {
                                onMarkerClick(data)
                            }
                            true
                        }
                    }
                    mapView.overlays.add(m)
                }

                selectedMarker?.let { target ->
                    locationOverlayRef?.myLocation?.let { myLoc ->
                        val routePoints = planRoute(
                            myLoc.latitude,
                            myLoc.longitude,
                            target.latitude,
                            target.longitude,
                            target.name
                        )

                        val line = Polyline(mapView).apply {
                            setPoints(routePoints.map { GeoPoint(it.first, it.second) })
                            outlinePaint.color = android.graphics.Color.RED
                            outlinePaint.strokeWidth = 12f
                        }
                        mapView.overlays.add(line)
                    }
                }
                locationOverlayRef?.myLocation?.let { myLoc ->
                    onLocationUpdate(myLoc.latitude, myLoc.longitude)
                }
                mapView.invalidate()
            }
        )

        if (selectedMarker != null) {
            val myLoc = locationOverlayRef?.myLocation
            if (myLoc != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    myLoc.latitude, myLoc.longitude,
                    selectedMarker.latitude, selectedMarker.longitude,
                    results
                )
                val distanceKm = results[0] / 1000
                val distText = if (distanceKm < 1) "${(distanceKm * 1000).toInt()} m" else "${
                    "%.2f".format(distanceKm)
                } km"

                Surface(
                    modifier = Modifier
                        .padding(top = 90.dp, start = 16.dp)
                        .align(Alignment.TopStart),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "距離: $distText",
                        color = Color.Green,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Surface(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .align(Alignment.TopCenter),
                    color = if (currentMode == NavigationMode.LAND) Color(0xFFE65100).copy(alpha = 0.85f) else Color(
                        0xFF0D47A1
                    ).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentMode == NavigationMode.LAND) "🚗 AI 辨識：陸地路網模式" else "🌊 AI 辨識：海域直線模式",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 右上角按鈕組
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isWarning = anomalyStatus.contains("⚠️")

            // 🤖 Edge AI 狀態監控灰色背景面板
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .background(
                        color = if (isWarning) Color.Red.copy(alpha = 0.25f) else Color.DarkGray.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
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

                Divider(color = Color.Gray.copy(alpha = 0.5f), thickness = 0.5.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = "演算法: 馬氏距離 (MD)", color = Color.White, fontSize = 9.sp)
                    Text(text = "時序視窗: 30s 滑動視窗", color = Color.White, fontSize = 9.sp)
                    Text(text = "監控維度: 速度 ✕ 航向率", color = Color.White, fontSize = 9.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 🚀 【完美合併版】單一智慧型 Edge AI 數據控制鈕（這顆按鈕必須在這個括號內！）
                Button(
                    onClick = {
                        val lat = locationOverlayRef?.myLocation?.latitude ?: 25.1
                        val lng = locationOverlayRef?.myLocation?.longitude ?: 121.5

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
                    modifier = Modifier.fillMaxWidth().height(28.dp),
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

            MapButton(if (isSatelliteMode) "一般模式" else "衛星模式", Color(0xFF6200EE)) {
                isSatelliteMode = !isSatelliteMode
            }

            MapButton("回到我的位置", Color(0xFF6200EE)) {
                locationOverlayRef?.myLocation?.let {
                    mapViewRef?.controller?.animateTo(it)
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

            Button(
                onClick = {
                    mapReference?.let { map ->
                        val tileSource = map.tileProvider.tileSource
                        if (tileSource.name() != "Mapnik") {
                            try {
                                val cache = CacheManager(map)
                                val currentZoom = map.zoomLevelDouble.toInt()
                                val boundingBox = map.boundingBox

                                scope.launch(Dispatchers.Main) {
                                    cache.downloadAreaAsync(
                                        context,
                                        boundingBox,
                                        currentZoom,
                                        currentZoom,
                                        object : CacheManager.CacheManagerCallback {
                                            override fun setPossibleTilesInArea(total: Int) {}
                                            override fun onTaskComplete() {
                                                Toast.makeText(context, "下載完成", Toast.LENGTH_SHORT).show()
                                            }
                                            override fun onTaskFailed(errors: Int) {
                                                Toast.makeText(context, "下載失敗", Toast.LENGTH_SHORT).show()
                                            }
                                            override fun updateProgress(p: Int, c: Int, z: Int, zoomMax: Int) {}
                                            override fun downloadStarted() {}
                                        }
                                    )
                                }
                                Toast.makeText(context, "開始背景預載...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                map.invalidate()
                            }
                        } else {
                            map.invalidate()
                            Toast.makeText(context, "已更新當前快取", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE),
                    contentColor = Color.White
                )
            ) {
                Text("預載此區")
            }
        }

        if (showMarkerDialog && tempLocation != null) {
            AlertDialog(
                onDismissRequest = { showMarkerDialog = false },
                title = { Text("新增釣點") },
                text = {
                    OutlinedTextField(
                        value = markerNameInput,
                        onValueChange = { markerNameInput = it },
                        label = { Text("點位名稱") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onMapClick(
                            tempLocation!!.latitude,
                            tempLocation!!.longitude,
                            markerNameInput
                        )
                        showMarkerDialog = false
                        markerNameInput = "新釣點"
                    }) { Text("確定") }
                }
            )
        }
    }
}