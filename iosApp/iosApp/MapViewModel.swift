import Foundation
import composeApp
import CoreLocation

@MainActor
class MapViewModel: NSObject, ObservableObject, CLLocationManagerDelegate { // 🎯 繼承 NSObject 與定位委派
    // 儲存從政府 API 抓回來的風場資料
    @Published var windFarmData: WindFarmGeoJson? = nil
    // 🎯 儲存目前最新 GPS 位置
    @Published var currentCoordinate: CLLocationCoordinate2D? = nil
    // 儲存是否正在轉圈圈
    @Published var isLoading: Bool = false
    // 🎯 先建立 detector，再注入進 sharedVM
    let detector: AnomalyDetector
    let sharedVM: SharedViewModel
    @Published var collisionAlert: String? = nil
    // 🎯 用來即時刷新 iOS UI 畫面的 AI 偵測狀態文字
    @Published var anomalyStatusText: String = "正常航行"
    // 🎯 新增離線天氣預警文字屬性
    @Published var weatherAlertText: String = "☀️ 氣壓穩定・天氣正常"
    // 🎯 建立純 Swift 的定位管理器
    private let locationManager = CLLocationManager()
    // 🎯 宣告為 [JobItem]
    @Published var jobsList: [JobItem] = []
    @Published var isLoadingJobs: Bool = false
    
    override init() {
        // 🎯 在 super.init() 之前初始化 KMP 類別
        self.detector = AnomalyDetector()
        self.sharedVM = SharedViewModel(detector: self.detector)
        
        super.init()
        // 🎯 初始化時立刻要求權限並啟動 GPS 監聽
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation() // 🚀 啟動！當 App 打開或移動時，會自動瘋狂觸發下面的更新函式
        
        // 🎯 實作 Swift 閉包傳給 Kotlin 的 expect/actual 機制
        self.detector.setupNativePredictor { (inputData: KotlinFloatArray) -> KotlinFloat in
            let mockScore: Float = 0.1
            return KotlinFloat(value: mockScore) // ✅ 使用 KotlinFloat 包裝回傳
        }
        
        // 🎯 監聽 Kotlin 的 AI 異常狀態水管
        self.sharedVM.watchAnomalyStatus { [weak self] (status: String) in
            guard let self = self else { return }
            self.anomalyStatusText = status
        }
                
        // 🎯 監聽離線氣象天氣預警水管！
        self.sharedVM.watchWeatherAlert { [weak self] (alert: String) in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.weatherAlertText = alert
            }
        }
        
        // 🎯 監聽 Kotlin 的 AI 異常狀態水管，即時同步到 SwiftUI
        self.sharedVM.watchAnomalyStatus { [weak self] (status: String) in
            guard let self = self else { return }
            self.anomalyStatusText = status
        }
        
        func checkCollisionAlert() {
            // 假設你 KMP 的 collisionAlert 是透過某種方式暴露
            // 如果你 KMP 直接有一個 getter，直接賦值即可
            // 例如: self.collisionAlert = sharedVM.collisionAlert.value
        }
    }
    
    // 🎯 提供給 iOS UI 呼叫的 API 方法
    func fetchCloudJobs() {
        self.isLoadingJobs = true
        // 呼叫 Kotlin SharedViewModel 的方法
        self.sharedVM.fetchCloudJobs()
        
        Task {
            // 等待 Kotlin 異步 API 請求完成
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            
            // 🎯  從 Kotlin StateFlow 取出 List 並轉型
            if let array = self.sharedVM.jobsList.value as? [JobItem] {
                DispatchQueue.main.async {
                    self.jobsList = array
                    self.isLoadingJobs = false
                }
            } else {
                DispatchQueue.main.async {
                    self.isLoadingJobs = false
                }
            }
        }
    }
    
    func fetchWindFarms() async {
        isLoading = true
        do {
            let result = try await ApiClient.shared.fetchWindFarmZones()
            self.windFarmData = result
            print("iOS 成功抓取風場數據")
        } catch {
            print("iOS 抓取失敗: \(error.localizedDescription)")
        }
        isLoading = false
    }
    
    // 🚀 當使用者移動、GPS 更新時自動觸發
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        let lat = location.coordinate.latitude
        let lng = location.coordinate.longitude
        self.currentCoordinate = location.coordinate
        
        // 🎯 對齊在 SharedViewModel 擴充的 4 個參數，將定位一併丟給 AI 緩衝器
        self.sharedVM.updateShipStatus(
            speedMps: location.speed,
            headingDegrees: location.course,
            lat: lat,
            lng: lng
        )
        
        // 🎯 觸發智慧防撞計算
        _ = sharedVM.planSmartRouteForIOS(
            currentLat: location.coordinate.latitude,
            currentLng: location.coordinate.longitude,
            targetLat: location.coordinate.latitude,
            targetLng: location.coordinate.longitude,
            targetName: "iOS 即時監測"
        )
        
        // 🚀 只要位置一改變（或剛打開抓到第一筆定位），立刻自我觸發 planSmartRoute！
        // 在尚未點擊任何目標前，目的地 targetLat/targetLng 可以先帶當前位置來進行當下環境辨識
        _ = self.planSmartRoute(
            currentLat: lat,
            currentLng: lng,
            targetLat: lat,
            targetLng: lng,
            targetName: "GPS 自動定位即時偵測"
        )
    }
    
    func planSmartRoute(currentLat: Double, currentLng: Double, targetLat: Double, targetLng: Double, targetName: String) -> [CLLocationCoordinate2D] {
        
        // 🎯 只要這個函式被執行，全天下最親切的這行 Log 就「一定」會噴出來！
        print("=== 🍏 iOS 偵測：開始觸發 planSmartRoute，目標是: \(targetName) ===")
        
        // 🎯 這裡直接呼叫 Kotlin 拿回純淨字串
        let routeString = sharedVM.planSmartRouteForIOS(
            currentLat: currentLat,
            currentLng: currentLng,
            targetLat: targetLat,
            targetLng: targetLng,
            targetName: targetName
        )
        
        // 🎯 看看 Kotlin 吐回來的字串長怎樣
        print("=== 🍏 iOS 偵測：Kotlin 回傳的原始字串為: \(routeString) ===")
        
        var coordinates: [CLLocationCoordinate2D] = []
        
        if !routeString.isEmpty {
            let pairs = routeString.components(separatedBy: ";")
            for pair in pairs {
                let coords = pair.components(separatedBy: ",")
                if coords.count == 2,
                   let lat = Double(coords[0]),
                   let lng = Double(coords[1]) {
                    coordinates.append(CLLocationCoordinate2D(latitude: lat, longitude: lng))
                }
            }
        }
        
        return coordinates
    }
    
    // 🎯 補上模擬遭遇暴流的方法
    func simulateAnomaly(lat: Double, lng: Double) {
        // 這裡確保呼叫的是 Kotlin SharedViewModel 的模擬方法
        self.sharedVM.simulateAnomaly(lat: lat, lng: lng)
    }
    
    // 🎯 補上恢復正常的方法
    func resetAnomaly(lat: Double, lng: Double) {
        // 這裡確保呼叫的是 Kotlin SharedViewModel 的恢復方法
        self.sharedVM.resetAnomaly(lat: lat, lng: lng)
    }
    
}
