import SwiftUI
import MapKit
import composeApp

struct ContentView: View {
    @State private var isNavigating = false
    @State private var selectedMapType: MKMapType = .standard
    @State private var isFollowMode = false
    @StateObject private var viewModel: IOSMapViewModel
    @StateObject var myKmpBridge = MapViewModel() // 🎯 建立你專屬新寫的 MapViewModel，用來做為 KMP 狀態監聽的橋樑
    @State private var showOfflineAlert = false
    @State private var mapView: MKMapView? = nil
    @State private var aiModeText: String = "🌊 AI 辨識：海域直線模式"  // 🎯 宣告一個用來即時刷新 UI 的變數，預設為海域模式
    @State private var isSimulatingPressureDrop = false // 🎯 紀錄是否正在模擬氣壓驟降
    
    init() {
        let bridge = MapViewModel()
        self._myKmpBridge = StateObject(wrappedValue: bridge)
        self._viewModel = StateObject(wrappedValue: IOSMapViewModel(sharedVM: bridge.sharedVM))
    }
    
    var body: some View {
        ZStack {
            AppleMapView(
                windFarmData: viewModel.windFarmData,
                mapType: selectedMapType,
                showRoute: $isNavigating,
                isFollowMode: $isFollowMode,
                viewModel: self.viewModel, // 傳給原本舊的 KMP IOSMapViewModel 參數
                kmpBridge: self.myKmpBridge, // 🎯 正確傳入你用來接 watchCurrentMode 水管的核心 MapViewModel！
                mapViewInstance: $mapView // 🎯 傳入剛定義的變數
            )
            .edgesIgnoringSafeArea(.all)
            
            // 🎯 防撞警報 UI
            if let alert = myKmpBridge.collisionAlert { // 假設你把警報加進了 myKmpBridge
                Text(alert)
                    .padding()
                    .background(Color.red.opacity(0.9))
                    .foregroundColor(.white)
                    .cornerRadius(10)
                    .padding(.top, 100) // 避開頂部安全區域
                    .transition(.move(edge: .top))
            }
            
            // 🎯 畫面頂部：AI 模式 + 天氣預警 (垂直乾淨排列)
            VStack(spacing: 6) {
                // 黑色 AI 陸海域辨識膠囊
                Text(aiModeText)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.75))
                    .cornerRadius(16)
                
                // 離線氣象預警膠囊 (放在 AI 膠囊正下方，不再重疊)
                WeatherAlertCapsule(alertText: myKmpBridge.weatherAlertText)
                
                Spacer()
            }
            .padding(.top, 15) // 避開 iPhone 上方動態島/時間區
            
            if viewModel.downloadProgress > 0 && viewModel.downloadProgress < 1 {
                VStack {
                    VStack {
                        ProgressView(value: viewModel.downloadProgress, total: 1.0)
                        Text("預載中... \(Int(viewModel.downloadProgress * 100))%")
                            .foregroundColor(.white)
                    }
                    .padding()
                    .background(Color.black.opacity(0.8))
                    .cornerRadius(10)
                    Spacer()
                }
                .padding(.top, 60)
            }
            
            // ✅ 使用這個組合將距離框推向左上角
            VStack {
                HStack {
                    if isNavigating {
                        VStack(alignment: .leading, spacing: 5) {
                            HStack(spacing: 4) {
                                Image(systemName: "lines.measurement.horizontal")
                                    .foregroundColor(.white)
                                Text("距離:")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            Text(viewModel.currentRouteDistance)
                                .font(.system(size: 24, weight: .black))
                                .foregroundColor(.green)
                        }
                        .padding()
                        .background(Color.black.opacity(0.85))
                        .cornerRadius(12)
                        .padding(.leading, 15)
                        .padding(.top, 120) // 🚀 關鍵：從 60 稍微下調到 120，不再擋到紅框！
                    }
                    Spacer()
                }
                Spacer()
            }
        
            HStack {
                Spacer()
                VStack(spacing: 15) {
                    // 🎯 AI 航行異常偵測的浮動燈號面板
                    VStack(spacing: 6) {
                        Text("🤖 AI 航行狀態")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.gray)
                        
                        let isWarning = myKmpBridge.anomalyStatusText.contains("⚠️")
                        
                        Text(myKmpBridge.anomalyStatusText)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(isWarning ? .red : .green)
                        
                        Divider()
                            .background(Color.gray.opacity(0.3))
                            .padding(.vertical, 2)
                        
                        // 📊 演算法詳細數據
                        VStack(alignment: .leading, spacing: 2) {
                            Text("演算法: 馬氏距離 (MD)").font(.system(size: 9)).foregroundColor(.gray)
                            Text("時序視窗: 30s 滑動視窗").font(.system(size: 9)).foregroundColor(.gray)
                            Text("監控維度: 速度 ✕ 航向率").font(.system(size: 9)).foregroundColor(.gray)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        
                        Spacer().frame(height: 4)
                        
                        // 💥 點擊發送異常資料的按鈕
                        Button(action: {
                            // 安全拿到地圖當前中心點位置作為發送座標
                            let lat = mapView?.centerCoordinate.latitude ?? 25.1
                            let lng = mapView?.centerCoordinate.longitude ?? 121.5
                            
                            if isWarning {
                                myKmpBridge.sharedVM.resetAnomaly(lat: lat, lng: lng)
                            } else {
                                myKmpBridge.sharedVM.simulateAnomaly(lat: lat, lng: lng)
                            }
                        }) {
                            Text(isWarning ? "✅ 恢復正常" : "💥 模擬遭遇暴流")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity, minHeight: 26)
                                .background(isWarning ? Color.green : Color.red)
                                .cornerRadius(6)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .frame(width: 145) // 固定面板寬度與 Compose 同步
                    .background(myKmpBridge.anomalyStatusText.contains("⚠️") ? Color.red.opacity(0.15) : Color.white.opacity(0.9))
                    .cornerRadius(12)
                    .shadow(color: Color.black.opacity(0.1), radius: 3, x: 0, y: 2)
                    .padding(.bottom, 5)
                    
                    MapControlButton(title: selectedMapType == .standard ? "衛星模式" : "一般模式") {
                        selectedMapType = (selectedMapType == .standard) ? .hybrid : .standard
                    }

                    // 🎯 回到我的位置：強制開啟追蹤模式
                    MapControlButton(title: "回到我的位置") {
                        isFollowMode = true
                    }

                    if isNavigating {
                        MapControlButton(title: "清除導航", color: .red) {
                            // 這裡設為 false 後，AppleMapView 的 updateUIView 會偵測到並清空線條
                            isNavigating = false
                            
                            // 可選：如果你希望清除後停止跟隨模式
                            isFollowMode = false
                            viewModel.showDistanceBottomInfo = false
                        }
                    }
                    
                    // 清空所有點位按鈕
                    MapControlButton(title: "清空所有點位", color: .gray.opacity(0.8)) {
                        // 增加一個簡單的二次確認 (選做)
                        viewModel.clearAllMarkers()
                        isNavigating = false
                        
                        // 可選：如果你希望清除後停止跟隨模式
                        isFollowMode = false
                        viewModel.showDistanceBottomInfo = false
                    }
                    
                    // 💥 測試模擬氣壓驟降
                    MapControlButton(
                        title: isSimulatingPressureDrop ? "恢復正常氣壓" : "模擬氣壓驟降",
                        color: isSimulatingPressureDrop ? .green : .orange
                    ) {
                        if isSimulatingPressureDrop {
                            // 恢復正常氣壓（連續寫入穩定氣壓，讓 sharedVM 判定氣壓平穩）
                            myKmpBridge.sharedVM.updateBarometerPressure(pressureHpa: 1013.2)
                            myKmpBridge.sharedVM.updateBarometerPressure(pressureHpa: 1013.2)
                            isSimulatingPressureDrop = false
                        } else {
                            // 模擬驟降（1013.2 -> 1009.7 觸發暴風雨告警）
                            myKmpBridge.sharedVM.updateBarometerPressure(pressureHpa: 1013.2)
                            myKmpBridge.sharedVM.updateBarometerPressure(pressureHpa: 1009.7)
                            isSimulatingPressureDrop = true
                        }
                    }
                    
                    MapControlButton(title: "預載此區") {
                        if let mv = self.mapView {
                            let region = mv.region
                            
                            let north = region.center.latitude + region.span.latitudeDelta / 2
                            let south = region.center.latitude - region.span.latitudeDelta / 2
                            let east = region.center.longitude + region.span.longitudeDelta / 2
                            let west = region.center.longitude - region.span.longitudeDelta / 2
                            
                            viewModel.sharedVM.downloadArea(
                                north: north,
                                south: south,
                                east: east,
                                west: west,
                                zoomLevels: KotlinIntRange(start: 10, endInclusive: 15)
                            )
                            self.showOfflineAlert = true
                        }
                    }
                    Spacer()
                }
                .padding(.trailing, 10)
                .padding(.top, 120)
            }
            .alert("新增釣點", isPresented: $viewModel.showAlert) {
                // 在 Alert 裡面加入輸入框
                TextField("請輸入點位名稱", text: $viewModel.newSpotName)
                
                Button("確定") {
                    viewModel.addMarkerAfterConfirm()
                }
                Button("取消", role: .cancel) {
                    viewModel.newSpotName = "" // 取消時清空
                }
            } message: {
                Text("您確定要在這裡新增 Marker 嗎？\n\(viewModel.lastClickedLocation)")
            }
            // 下方距離資訊彈窗 (由 isPresented 控制)
            VStack {
                Spacer()
                if viewModel.showDistanceBottomInfo {
                    HStack(spacing: 12) {
                        // ✅ 這裡將名稱與距離合併成一行黑字，移除原本下方的白字
                        Text("距離 \(viewModel.selectedSpotNameForDistance) : \(viewModel.currentRouteDistance)")
                            .font(.system(size: 16, weight: .bold)) // 加粗讓資訊更清晰
                            .foregroundColor(.black) // 👈 強制設定為黑色，解決背景變白看不見的問題
                        
                        // 修改名稱的小圖示按鈕 (保留原本功能)
                        Button(action: {
                            if let currentAnnotation = viewModel.savedMarkers.first(where: {
                                $0.title == viewModel.selectedSpotNameForDistance
                            }) {
                                viewModel.prepareEditName(for: currentAnnotation)
                            }
                        }) {
                            Image(systemName: "pencil.circle.fill")
                                .foregroundColor(.blue)
                        }
                        
                        Spacer()
                        
                        // 關閉按鈕
                        Button("關閉") {
                            viewModel.showDistanceBottomInfo = false
                        }
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.purple)
                    }
                    .padding()
                    .frame(minWidth: 280) // 保持與截圖一致的寬度
                    .background(Color.white.opacity(0.95)) // 使用較高不透明度的白底
                    .cornerRadius(20)
                    .shadow(color: .black.opacity(0.15), radius: 10, x: 0, y: 5)
                    .padding(.bottom, 40)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .alert("修改名稱", isPresented: $viewModel.showEditNameAlert) {
                TextField("輸入新名稱", text: $viewModel.tempEditingName)
                Button("確定") {
                    viewModel.confirmRename()
                }
                Button("取消", role: .cancel) { }
            }
            .animation(.spring(), value: viewModel.showDistanceBottomInfo) // 開關動畫
        }
        .onAppear {
            // 🚀 修正關鍵：經由 myKmpBridge 肚子裡的 sharedVM 去啟動監聽水管！
            myKmpBridge.sharedVM.watchCurrentMode { modeString in
                DispatchQueue.main.async {
                    if modeString == "LAND" {
                        self.aiModeText = "🚗 AI 辨識：陸地路網模式"
                    } else {
                        self.aiModeText = "🌊 AI 辨識：海域直線模式"
                    }
                    print("=== 🍏 iOS 成功接收推播：目前導航狀態更新為 -> [\(modeString)] ===")
                }
            }
        }
        .task {
            await viewModel.fetchWindFarms()
        }
    }
}

// 🎯 自定義美化按鈕元件
struct MapControlButton: View {
    let title: String
    var color: Color = Color.purple.opacity(0.8)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 100, height: 40)
                .background(color)
                .cornerRadius(20) // 圓角矩形樣式
                .shadow(radius: 3)
        }
    }
}

@MainActor
class IOSMapViewModel: ObservableObject {
    @Published var windFarmData: WindFarmGeoJson? = nil
    @Published var savedMarkers: [MKPointAnnotation] = [] // iOS 端的標記列表
    private var pendingLocation: CLLocationCoordinate2D? // 暫存點擊的位置
    @Published var newSpotName: String = ""// 綁定輸入框的文字
    @Published var downloadProgress: Float = 0.0
    // 這是來自 Kotlin 的 SharedViewModel
    let sharedVM: SharedViewModel
    // 顯示 Alert 的狀態
    @Published var showAlert = false
    @Published var lastClickedLocation: String = ""
    // 顯示距離資訊
    @Published var currentRouteDistance: String = "0.00 km"
    @Published var selectedSpotNameForDistance: String = "" // 下方彈窗用的名字
    @Published var showDistanceBottomInfo: Bool = false // 控制下方彈窗顯示
    @Published var showEditNameAlert = false
    @Published var editingMarker: MKPointAnnotation? = nil // 紀錄正在改哪一個
    @Published var tempEditingName: String = ""
    
    init(sharedVM: SharedViewModel) {
        // 先抓取 Kotlin 裡的原始資料
        self.sharedVM = sharedVM
        let spots = sharedVM.markerList.value
        
        // 明確指定為 [FishingSpot] 列表進行轉換
        if let fishingSpots = spots as? [FishingSpot] {
            self.savedMarkers = fishingSpots.map { spot in
                let annotation = MKPointAnnotation()
                annotation.coordinate = CLLocationCoordinate2D(latitude: spot.lat, longitude: spot.lng)
                annotation.title = spot.name
                return annotation
            }
        }
        
        sharedVM.watchProgress { progress in
            // progress 是 Kotlin 傳過來的 Float，Swift 這裡會識別為 Float
            DispatchQueue.main.async {
                self.downloadProgress = Float(truncating: progress as! NSNumber)
            }
        }
        
    }
    
    // 準備編輯
    func prepareEditName(for annotation: MKPointAnnotation) {
        self.editingMarker = annotation
        self.tempEditingName = annotation.title ?? ""
        self.showEditNameAlert = true
    }
    
    // 確認修改
    func confirmRename() {
        guard let marker = editingMarker else { return }
        let newName = tempEditingName.isEmpty ? "未命名點位" : tempEditingName
        
        // 更新介面
        marker.title = newName
        self.selectedSpotNameForDistance = newName
        
        // ✅ 呼叫 Kotlin 剛剛寫好的新方法
        sharedVM.updateSpotName(
            lat: marker.coordinate.latitude,
            lng: marker.coordinate.longitude,
            newName: newName
        )
        
        self.editingMarker = nil
    }
    
    func clearAllMarkers() {
        // 叫 Kotlin 清空資料與存檔
        sharedVM.clearAllSpots()
        
        // 清空 iOS 畫面上的大頭針
        self.savedMarkers.removeAll()
        
        // 如果正在導航，順便把導航也關了
        self.showDistanceBottomInfo = false
        
        // 觸發震動回饋
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
    }

    func fetchWindFarms() async {
        do {
            let result = try await sharedVM.getWindFarmData()
            self.windFarmData = result
            print("✅ 成功拿到資料：\(result.features.count) 筆")
        } catch {
            print("❌ 抓取失敗，錯誤訊息：\(error.localizedDescription)")
        }
    }

    func saveSpot(lat: Double, lng: Double, name: String) {
        sharedVM.saveSpot(lat: lat, lng: lng, name: name)
    }
    
    func addMarkerAfterConfirm() {
        guard let loc = pendingLocation else { return }
        
        // 取得名稱，若為空則給預設值
        let finalName = newSpotName.isEmpty ? "未命名點位" : newSpotName
        
        sharedVM.saveSpot(lat: loc.latitude, lng: loc.longitude, name: finalName)
        
        // 更新 iOS 本地的畫圖列表
        let newAnnotation = MKPointAnnotation()
        newAnnotation.coordinate = loc
        newAnnotation.title = finalName
        self.savedMarkers.append(newAnnotation)
        
        // 重置
        self.newSpotName = ""
        self.pendingLocation = nil
    }

    func handleMapTap(lat: Double, lng: Double) {
        // 暫存座標
        self.pendingLocation = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        
        // 格式化文字顯示在彈窗上
        self.lastClickedLocation = String(format: "緯度: %.4f, 經度: %.4f", lat, lng)
        
        // 給予預設值，使用者一打開彈窗就會看到
        self.newSpotName = "我的新釣點"
        
        // 觸發彈窗顯示
        self.showAlert = true
    }
    
    // 計算距離的方法 (由 AppleMapView 呼叫)
    func updateRouteDistance(from source: CLLocationCoordinate2D, destination: CLLocationCoordinate2D, name: String) {
        let sourceLoc = CLLocation(latitude: source.latitude, longitude: source.longitude)
        let destLoc = CLLocation(latitude: destination.latitude, longitude: destination.longitude)
        
        // 計算直線距離 (單位：公尺)
        let distanceInMeters = sourceLoc.distance(from: destLoc)
        
        // 轉為公里並格式化
        let distanceInKm = distanceInMeters / 1000.0
        let formattedDistance = String(format: "%.2f km", distanceInKm)
        
        // 更新狀態
        self.currentRouteDistance = formattedDistance
        self.selectedSpotNameForDistance = name
        self.showDistanceBottomInfo = true // 點擊時同步彈出下方視窗
    }
    
}

// 🎯 離線氣象天氣預警膠囊元件
struct WeatherAlertCapsule: View {
    let alertText: String
    
    var isWarning: Bool {
        alertText.contains("⚠️") || alertText.contains("🌦️")
    }
    
    var body: some View {
        HStack(spacing: 8) {
            Text(alertText)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(isWarning ? .yellow : .white)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(
            isWarning
            ? Color.red.opacity(0.85)
            : Color.black.opacity(0.65)
        )
        .cornerRadius(20)
        .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
        .animation(.easeInOut, value: alertText)
    }
}
