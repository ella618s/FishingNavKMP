import Foundation
import composeApp

@MainActor
class MapViewModel: ObservableObject {
    // 儲存從政府 API 抓回來的風場資料
    @Published var windFarmData: WindFarmGeoJson? = nil
    
    // 🎯 補上這行！這樣 ContentView 才能判斷是否正在轉圈圈
    @Published var isLoading: Bool = false
    
    func fetchWindFarms() async {
        // 開始抓資料，設為 true
        isLoading = true
        
        do {
            let result = try await ApiClient.shared.fetchWindFarmZones()
            self.windFarmData = result
            print("iOS 成功抓取風場數據")
        } catch {
            print("iOS 抓取失敗: \(error.localizedDescription)")
        }
        
        // 抓完了（不論成功或失敗），設回 false
        isLoading = false
    }
}
