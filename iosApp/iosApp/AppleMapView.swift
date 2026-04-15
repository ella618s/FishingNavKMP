import SwiftUI
import MapKit
import composeApp

struct AppleMapView: UIViewRepresentable {
    var windFarmData: WindFarmGeoJson?
    var mapType: MKMapType
    @Binding var showRoute: Bool
    @Binding var isFollowMode: Bool
    @ObservedObject var viewModel: IOSMapViewModel
    @State var currentRoute: MKRoute? = nil // 存放計算好的路線
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = true
        
        // 修正藍屏：初始化時不使用尚未確定的 userLocation
        // 設定一個預設的台灣座標，防止地圖飛走
        let defaultRegion = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 25.0330, longitude: 121.5654),
            latitudinalMeters: 5000,
            longitudinalMeters: 5000
        )
        mapView.setRegion(defaultRegion, animated: false)
        
        // 啟動時強制開啟跟隨模式，確保光束出現
        mapView.setUserTrackingMode(.followWithHeading, animated: false)
        // 點擊手勢
        let tapGesture = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        mapView.addGestureRecognizer(tapGesture)
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        if uiView.mapType != mapType { uiView.mapType = mapType }
        
        // 只有按下「回到我的位置」按鈕時，才執行強制吸回與旋轉
        if isFollowMode {
            uiView.setUserTrackingMode(.followWithHeading, animated: true)
            DispatchQueue.main.async {
                self.isFollowMode = false
            }
        }
        
        let oldAnnotations = uiView.annotations.filter { !($0 is MKUserLocation) }
        uiView.removeAnnotations(oldAnnotations)
        uiView.addAnnotations(viewModel.savedMarkers)
        
        // 風場渲染
        updateWindFarmOverlays(uiView)
        
        // 處理導航線條
        uiView.removeOverlays(uiView.overlays.filter { $0 is MKPolyline }) // 先清舊線
        if let route = currentRoute {
            uiView.addOverlay(route.polyline)
        }
        
        // 處理路徑線條的顯示與清除
        if showRoute {
            // 如果有路徑且地圖上還沒畫，就加上去
            if let route = currentRoute, uiView.overlays.isEmpty {
                uiView.addOverlay(route.polyline)
                // 自動縮放地圖以看見整條路線
                uiView.setVisibleMapRect(route.polyline.boundingMapRect, edgePadding: UIEdgeInsets(top: 50, left: 50, bottom: 50, right: 50), animated: true)
            }
        } else {
            // 當 showRoute 變為 false (點擊清除按鈕) 時，移除所有線條
            uiView.removeOverlays(uiView.overlays)
            context.coordinator.parent.currentRoute = nil // 清空暫存
        }
    }
    
    private func updateWindFarmOverlays(_ mapView: MKMapView) {
        mapView.removeOverlays(mapView.overlays.filter { $0 is MKPolygon })
        guard let features = windFarmData?.features else { return }
        for feature in features {
            guard let coordinates = feature.geometry.coordinates.first else { continue }
            let points = coordinates.map { coord in
                CLLocationCoordinate2D(latitude: coord[1].doubleValue, longitude: coord[0].doubleValue)
            }
            let polygon = MKPolygon(coordinates: points, count: points.count)
            mapView.addOverlay(polygon)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        return Coordinator(self) // 將 self (AppleMapView) 傳入
    }
    
    class Coordinator: NSObject, MKMapViewDelegate, CLLocationManagerDelegate {
        // 1. 新增對 parent 的引用
        var parent: AppleMapView
        let locationManager = CLLocationManager()
        // 2. 修改 init，接收 parent
        init(_ parent: AppleMapView) {
            self.parent = parent
            super.init()
            locationManager.delegate = self
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.requestWhenInUseAuthorization()
            
            if CLLocationManager.headingAvailable() {
                locationManager.startUpdatingHeading()
            }
        }
        
        func calculateRoute(from source: CLLocationCoordinate2D, to destination: CLLocationCoordinate2D) {
            let request = MKDirections.Request()
            request.source = MKMapItem(placemark: MKPlacemark(coordinate: source))
            request.destination = MKMapItem(placemark: MKPlacemark(coordinate: destination))
            request.transportType = .automobile
            
            let directions = MKDirections(request: request)
            directions.calculate { response, error in
                guard let route = response?.routes.first else { return }
                
                DispatchQueue.main.async {
                    // 1. 存入路徑
                    self.parent.currentRoute = route
                    // 2. 同步將 ContentView 的導航狀態設為 true，按鈕就會出現
                    self.parent.showRoute = true
                }
            }
        }
        
        // 3. 新增點擊處理函數
        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let mapView = gesture.view as? MKMapView else { return }
            let touchPoint = gesture.location(in: mapView)
            let coordinate = mapView.convert(touchPoint, toCoordinateFrom: mapView)
            let hitView = mapView.hitTest(touchPoint, with: nil)
            
            if let annotationView = hitView as? MKAnnotationView,
               let annotation = annotationView.annotation,
               !(annotation is MKUserLocation) {
                
                let title = (annotation.title ?? "") ?? "未命名"
                print("🎯 點擊到現有標記：\(title)")
                
                // 畫導航線 (從目前位置到該標記)
                if let userLocation = mapView.userLocation.location?.coordinate {
                    self.calculateRoute(from: userLocation, to: annotation.coordinate)
                    // 計算距離並通知 ViewModel 顯示 UI
                    self.parent.viewModel.updateRouteDistance(
                        from: userLocation,
                        destination: annotation.coordinate,
                        name: title
                    )
                }
                
                // 觸發觸覺回饋
                let generator = UIImpactFeedbackGenerator(style: .light)
                generator.impactOccurred()
                
            } else {
                // 點在空白處，跳出新增 Marker 的彈窗
                print("📍 點擊空白處，準備新增標記")
                
                // 呼叫原本的彈窗邏輯
                parent.viewModel.handleMapTap(lat: coordinate.latitude, lng: coordinate.longitude)
                
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
            }
        }
        
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = .systemBlue // 導航線顏色
                renderer.lineWidth = 5
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}
