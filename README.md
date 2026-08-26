# FishingNav KMP - 離線航海導航與雲端整合系統

[English Version (英文版說明)](./README_EN.md)

本專案採用 **Kotlin Multiplatform (KMP)** 架構開發，專為漁船在海上無網路環境下的導航、漁標記錄、安全監控，以及雲端實時海象測站與社群漁場數據整合設計。

## 🛠️ 技術亮點
* **KMP 跨平台架構**：實現 Android/iOS 核心邏輯共享，包含地理座標計算、資料模型管理與 API 資料串接。
* **響應式狀態管理 (Compose + Flow)**：利用 `StateFlow` 結合 `collectAsState` 實現跨平台邏輯與 UI 的即時同步。
* **跨平台網路層 (Ktor 2.3.12)**：統一處理跨平台 RESTful API 請求，搭配 `kotlinx-serialization` 進行 JSON 解析。
* **離線地圖引擎 (OSMDroid)**：深度整合 Android 原生 OSMDroid 框架，支援本地快取與離線衛星圖資渲染。
* **現代化 UI 實作**：全專案採用 Jetpack Compose 與 SwiftUI，具備流暢的互動體驗與 `LazyColumn` / List 可滑動視窗。

## 🛠️ 核心技術突破 (Android/iOS 跨平台與雲端 API 整合)
針對 OSMDroid 在 Compose 環境下的穩定性、KMP 狀態同步與跨平台實體機適配，本專案成功解決以下挑戰：

* **跨平台資料源同步 (Single Source of Truth)**：為解決原生 View 與 Compose/SwiftUI 狀態不同步的問題，將資料重心移至 `SharedViewModel`，透過 `StateFlow` 強制觸發 UI 更新，實現「一處修改，兩端同步」。
* **Render 雲端 Go + PostgreSQL API 跨平台串接 (全台海象與社群漁場)**：
  * 使用 Ktor 在 `commonMain` 實作異步 RESTful 客戶端（`JobApiClient`），對接部署於 Render 的 Go Backend 與 PostgreSQL 雲端資料庫。
  * 成功整合全台 31 筆氣象署海象測站實時數據與社群漁場點位，並在 KMP 共享層處理 JSON 自動序列化 (`SeaConditionResponse` / `CommunitySpotResponse`)，讓 Android (Compose) 與 iOS (SwiftUI) 能以極低的成本共享雲端 API 存取邏輯。
* **iOS 實體機 (Arm64) 編譯與類型對接**：
  * 克服 Objective-C / Swift 與 Kotlin Native 泛型轉型的相容性問題，透過封裝 Helper 方法直接將 Kotlin 的 `List<SeaCondition>` 與 `List<CommunitySpot>` 轉化為 Swift 原生型別。
  * 完整適配 iOS 16+ `.presentationDetents` 彈窗與 SwiftUI 狀態監聽機制，確保在實體 iPhone 上順利編譯與發布。
* **地圖互動 UI 優化 (Custom Marker Interaction)**：
  * 成功阻斷 OSMDroid 預設的 InfoWindow 彈窗，改以 Compose `AlertDialog` 實作自定義改名邏輯。
  * 實作「雙擊觸發」機制：點選標記進入導航模式，再次點選則開啟改名視窗，優化觸控體驗。
* **異步 UI 衝突修復 (WindowLeaked)**：解決了 `downloadAreaAsync` 背景下載時 Dialog 生命週期崩潰問題。透過自定義 `CacheManagerCallback` 與 `CoroutineScope` 實作靜默下載模式。
* **跨平台瓦片預載演算法**：在 `SharedViewModel` 中實作 Mercator 投影公式，將經緯度範圍精確轉換為瓦片座標 (X, Y)，並透過 Ktor 進行異步多線程下載。
* **自動 GPS 定位智慧路網辨識 (Smart Route Auto-Detection)**：
  * 實現「App 啟動即時偵測」與「座標變更動態觸發」核心機制。
  * 當系統抓取到第一筆 GPS 或位置發生改變時，自動將經緯度送入 KMP `SharedViewModel` 運算，擺脫傳統必須依賴點擊畫線才能觸發的限制。
  * 透過跨平台 `StateFlow` 監聽，讓 UI 層能即時接收狀態推播，在「陸地路網模式 (LAND)」與「海域直線模式 (SEA)」之間進行流暢無縫的動態切換。
* **智慧地理圍欄防撞 (Geofencing Proximity Alert)**：
  * **核心邏輯**：基於 KMP 共享層的 `GisGeometryUtils`，實作了「未來航跡預測演算法」。
  * **運作機制**：透過 `predictFutureLocation` 預測船隻未來 3 分鐘內的移動向量，並與風場 GeoJSON 邊界進行線段交叉檢測 (Intersection Detection)。
  * **跨平台同步**：Android 端透過 `MainActivity` 的 `LocationListener` 即時餵入航速與航向；iOS 端則透過 `CLLocationManager` 同步至 `SharedViewModel`。
  * **主動告警**：一旦判定航線將穿越風場，透過 `StateFlow` 即時觸發 UI 紅色警報，有效提升夜間與霧天的航海安全。
* **AI 航行異常與氣象驟降預警機制 (AI Anomaly & Weather Alert System)**：
  * **雙端同步面板**：在 KMP 共享層利用 `SharedViewModel` 維持單一異常狀態水管（`anomalyStatus`）與離線天氣預警水管（`weatherAlert`），實現 Android 與 iOS 雙端即時狀態連動。
  * **離線氣壓時序分析**：實作 `updateBarometerPressure` 氣壓變率演算法，自動維護 3 小時內的離線氣壓歷史快照。當偵測到氣壓急遽驟降（如 >3.0 hPa）時，自動觸發暴風雨/瘋狗浪預警警報。
  * **雙向模擬與完全重置機制**：於 KMP 核心注入 `simulateAnomaly`、`simulateBarometerDrop` 與 `resetAnomaly` 方法。成功打通 Native UI（Compose / SwiftUI）動態模擬氣壓驟降與極端洋流的控制鏈，並可一鍵重置滑動視窗緩衝器與告警 UI 狀態。

## 📱 互動式 GIS 與雲端功能展示
* **動態標籤渲染**：解析政府 Open Data 之風場區域 (GeoJSON)，實作半透明多邊形與標籤化渲染。
* **實時導航線繪製**：根據當前 GPS 位置與目標漁標，動態繪製 `Polyline` 並即時計算航行距離。
* **衛星圖資下載**：支援特定區域之衛星圖資預載，確保海上完全無網路環境下仍有視覺背景參考。
* **Render 雲端 API 整合**：點擊「☁️ 雲端 API」即可跨平台連線 Go + PostgreSQL 後端，即時載入並以 `LazyColumn` 流暢呈現場域 31 筆實態海象測站與社群漁場點位。

### Android 與 iOS 運行畫面
| Android 衛星導航 | iOS 地圖模式 |
| :---: | :---: |
| ![Android Screen](./screenshots/android_demo.png) | ![iOS Screen](./screenshots/ios_demo.png) |

### KMP 跨平台架構
![KMP Architecture](./screenshots/kmp_structure.png)

## 🏗️ 目前開發狀態
- [x] 跨平台 KMP 專案基礎環境架構與 Ktor 2.3.12 整合
- [x] SharedViewModel 跨平台狀態同步邏輯 (StateFlow)
- [x] OSMdroid 離線地圖底層渲染引擎 (Android)
- [x] Android 端漁標自定義改名與持久化儲存 (MarkerStorage)
- [x] Android 端衛星圖資異步下載與緩存機制 (穩定版)
- [x] 行政院政府 Open Data 風場資訊自動同步 (GeoJSON)
- [x] 實時方位校正與距離計算演算法 (Kotlin Shared Logic)
- [x] iOS 端 Apple Maps 整合與實時定位數據對接
- [x] iOS 端離線下載 UI (ProgressView) 與邏輯對接
- [x] 自動 GPS 定位智慧路網辨識與即時模式動態切換 (LAND/SEA)
- [x] AI 航行異常偵測狀態水管與 Android/iOS 雙端 UI 燈號即時連動
- [x] 離線氣壓時序趨勢分析與暴風雨驟降告警機制
- [x] 氣體與海域異常狀態雙向模擬控制水管與雙端 UI 一鍵響應式重置
- [x] Render 雲端 Go/PostgreSQL RESTful API 跨平台連線（全台 31 筆海象測站與漁場）
- [x] Android (Compose LazyColumn) 與 iOS (SwiftUI Sheet) 雙端可滑動 API 數據列表呈現
- [x] iOS 實體機 (Arm64) 構建、Framework 自動嵌入與適配

## 📄 授權與聲明 (License & Disclaimer)
* **版權所有**：© 2026 Ella Liu. All rights reserved.
* **僅供面試展示**：本專案程式碼與架構僅作為個人技術作品集與面試展示使用。