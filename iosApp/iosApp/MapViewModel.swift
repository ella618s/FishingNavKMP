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
    let sharedVM = SharedViewModel()
    @Published var collisionAlert: String? = nil
    
    // 🎯 建立純 Swift 的定位管理器
    private let locationManager = CLLocationManager()
    
    override init() {
        super.init()
        // 🎯 初始化時立刻要求權限並啟動 GPS 監聽
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation() // 🚀 啟動！當 App 打開或移動時，會自動瘋狂觸發下面的更新函式
        func checkCollisionAlert() {
            // 假設你 KMP 的 collisionAlert 是透過某種方式暴露
            // 如果你 KMP 直接有一個 getter，直接賦值即可
            // 例如: self.collisionAlert = sharedVM.collisionAlert.value
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
    
    // 🎯【核心關鍵】：App 一打開取得定位、或是座標改變時，iOS 系統會自動執行這個函式！
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        let lat = location.coordinate.latitude
        let lng = location.coordinate.longitude
        self.currentCoordinate = location.coordinate
        
        // 🎯 餵資料給 KMP 的核心大腦
        sharedVM.updateShipStatus(
            speedMps: location.speed < 0 ? 0 : location.speed, // iOS 速度若未知會是 -1
            headingDegrees: location.course
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
}
