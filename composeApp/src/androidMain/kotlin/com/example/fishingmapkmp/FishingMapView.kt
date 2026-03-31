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

@Composable
actual fun FishingMapView(
    modifier: Modifier,
    initialCenter: Pair<Double, Double>,
    markerList: List<CustomMarker>,
    selectedMarker: CustomMarker?,
    onMapClick: (Double, Double, String) -> Unit,
    onMarkerClick: (CustomMarker?) -> Unit,
    onLocationUpdate: (Double, Double) -> Unit, // 🎯 新增這一行：用來傳回目前 GPS 座標
    onRenameClick: (CustomMarker, String) -> Unit // 🎯 新增參數
) {
    val context = LocalContext.current
    var isSatelliteMode by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val windFarmData = remember { mutableStateOf<WindFarmGeoJson?>(null) }

    // --- 🎯 6. 補回：新增點位名稱輸入視窗相關狀態 ---
    var showMarkerDialog by remember { mutableStateOf(false) }
    var tempLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var markerNameInput by remember { mutableStateOf("新釣點") }
    // 1. 建立改名用的狀態
    var showRenameDialog by remember { mutableStateOf(false) }
    var markerToRename by remember { mutableStateOf<CustomMarker?>(null) }
    var newNameInput by remember { mutableStateOf("") }
    var mapReference by remember { mutableStateOf<MapView?>(null) }
    val scope = rememberCoroutineScope()

    // 2. 改名對話框 (UI 層)
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

                    // 🎯 修正點：必須在這裡 new，傳入 this 就不會閃退
                    val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)

                    // --- 恢復你原本的綠色三角形繪製邏輯 ---
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
                        moveTo(30f, 5f); lineTo(50f, 55f); lineTo(30f, 45f); lineTo(10f, 55f); close()
                    }
                    canvas.drawPath(path, p)
                    overlay.setDirectionArrow(bit, bit)
                    overlay.setDirectionAnchor(0.5f, 0.5f)
                    overlay.setPersonAnchor(0.5f, 0.5f)
                    overlay.enableMyLocation()
                    overlays.add(overlay)

                    locationOverlayRef = overlay // 存入引用
                    // -----------------------------------

                    // 恢復指南針
                    val compass = CompassOverlay(ctx, this)
                    compass.enableCompass()
                    overlays.add(compass)

                    // 🎯 3. 地圖點擊監聽
                    val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            p?.let { tempLocation = it; markerNameInput = "新釣點"; showMarkerDialog = true }
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
                // 🎯 4. 衛星/一般模式切換
                if (isSatelliteMode) {
                    mapView.setTileSource(object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
                        "Google-Satellite", 0, 20, 256, ".jpg",
                        arrayOf("https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}")
                    ) {
                        override fun getTileURLString(pTileIndex: Long): String = baseUrl
                            .replace("{x}", org.osmdroid.util.MapTileIndex.getX(pTileIndex).toString())
                            .replace("{y}", org.osmdroid.util.MapTileIndex.getY(pTileIndex).toString())
                            .replace("{z}", org.osmdroid.util.MapTileIndex.getZoom(pTileIndex).toString())
                    })
                } else {
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                }

                // 🎯 5. 繪製標記與導航線
                mapView.overlays.removeAll { it is Marker || it is Polyline }

                // 如果 API 資料抓到了，就畫在地圖上
                windFarmData.value?.let { data ->
                    // 避免重複加入，先清除舊的風場圖層 (如果你有給圖層標籤的話)
                    // map.overlays.removeAll { it is Polygon }

                    data.features.forEach { feature ->
                        val osmdroidPolygon = org.osmdroid.views.overlay.Polygon()
                        val points = mutableListOf<GeoPoint>()

                        // 解析 GeoJSON 座標 [lng, lat] -> GeoPoint(lat, lng)
                        feature.geometry.coordinates.firstOrNull()?.forEach { coord ->
                            points.add(GeoPoint(coord[1], coord[0]))
                        }

                        osmdroidPolygon.points = points
                        osmdroidPolygon.fillPaint.color = android.graphics.Color.parseColor("#406200EE") // 半透明紫
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
                        setOnMarkerClickListener { marker, _ ->
                            // 🎯 核心修正 2：判斷邏輯
                            if (selectedMarker?.latitude == data.latitude &&
                                selectedMarker?.longitude == data.longitude) {
                                onMarkerClick(data)
                                markerToRename = data
                                newNameInput = data.name
                                showRenameDialog = true
                            } else {
                                // 否則，執行原本的導航邏ctions
                                onMarkerClick(data)
                            }
                            true
                        }
                    }
                    mapView.overlays.add(m)
                }

                // 🎯 修正點 3：畫導航線 (僅在非 null 時)
                selectedMarker?.let { target ->
                    locationOverlayRef?.myLocation?.let { myLoc ->
                        val line = Polyline(mapView).apply {
                            setPoints(listOf(myLoc, GeoPoint(target.latitude, target.longitude)))
                            outlinePaint.color = android.graphics.Color.RED
                            outlinePaint.strokeWidth = 12f
                        }
                        mapView.overlays.add(line)
                    }
                }
                // 🎯 把目前的 GPS 位置傳回給 App.kt
                locationOverlayRef?.myLocation?.let { myLoc ->
                    onLocationUpdate(myLoc.latitude, myLoc.longitude)
                }
                mapView.invalidate()
            }
        )

        if (selectedMarker != null) {
            val myLoc = locationOverlayRef?.myLocation
            if (myLoc != null) {
                // 計算距離（公里）
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    myLoc.latitude, myLoc.longitude,
                    selectedMarker.latitude, selectedMarker.longitude,
                    results
                )
                val distanceKm = results[0] / 1000
                val distText = if (distanceKm < 1) "${(distanceKm * 1000).toInt()} m" else "${"%.2f".format(distanceKm)} km"

                Surface(
                    modifier = Modifier
                        .padding(top = 90.dp, start = 16.dp) // 避開指南針
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
            }
        }

        // 🎯 6. 右上角按鈕組 (紫色圓角)
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 衛星模式文字隨狀態切換
            MapButton(if (isSatelliteMode) "一般模式" else "衛星模式", Color(0xFF6200EE)) {
                isSatelliteMode = !isSatelliteMode
            }

            MapButton("回到我的位置", Color(0xFF6200EE)) {
                locationOverlayRef?.myLocation?.let {
                    mapViewRef?.controller?.animateTo(it)
                }
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

                        // 只有在衛星模式（非 Mapnik）時才執行背景下載
                        if (tileSource.name() != "Mapnik") {
                            try {
                                val cache = CacheManager(map)
                                val currentZoom = map.zoomLevelDouble.toInt()
                                val boundingBox = map.boundingBox

                                // 🎯 修正閃退：必須在 Main 執行緒發起，osmdroid 內部才能正確建立 Dialog 實體
                                scope.launch(Dispatchers.Main) {
                                    cache.downloadAreaAsync(
                                        context,
                                        boundingBox,
                                        currentZoom,
                                        currentZoom,
                                        object : CacheManager.CacheManagerCallback {
                                            // 補齊新版本要求的 setPossibleTilesInArea
                                            override fun setPossibleTilesInArea(total: Int) {}

                                            override fun onTaskComplete() {
                                                Toast.makeText(context, "下載完成", Toast.LENGTH_SHORT).show()
                                            }

                                            override fun onTaskFailed(errors: Int) {
                                                Toast.makeText(context, "下載失敗", Toast.LENGTH_SHORT).show()
                                            }

                                            // 補齊 4 個參數版本的 updateProgress
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
                            // 一般模式下只做刷新
                            map.invalidate()
                            Toast.makeText(context, "已更新當前快取", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                // 🎨 修正顏色紅字：Material 2 使用 backgroundColor
                colors = ButtonDefaults.buttonColors(
                    Color(0xFF6200EE), // 第一個位置通常是背景色
                    Color.White        // 第二個位置通常是內容(文字)色
                )
            ) {
                Text("預載此區")
            }
        }

        // --- 🎯 7. 補回：新增點位的彈跳視窗 (AlertDialog) ---
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
                        onMapClick(tempLocation!!.latitude, tempLocation!!.longitude, markerNameInput)
                        showMarkerDialog = false
                        markerNameInput = "新釣點"
                    }) { Text("確定") }
                }
            )
        }
    }
}