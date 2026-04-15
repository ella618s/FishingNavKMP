# FishingNav KMP - 離線航海導航系統

本專案採用 **Kotlin Multiplatform (KMP)** 架構開發，專為漁船在海上無網路環境下的導航、漁標記錄與安全監控設計。

## 🛠️ 技術亮點
* **KMP 跨平台架構**：實現 Android/iOS 核心邏輯共享，包含地理座標計算與資料模型管理。
* **響應式狀態管理 (Compose + Flow)**：利用 `StateFlow` 結合 `collectAsState` 實現跨平台邏輯與 UI 的即時同步。
* **跨平台網路層 (Ktor 2.3.12)**：統一處理跨平台 API 請求，搭配 `kotlinx-serialization` 進行 JSON 解析。
* **離線地圖引擎 (OSMDroid)**：深度整合 Android 原生 OSMDroid 框架，支援本地快取與離線衛星圖資渲染。
* **現代化 UI 實作**：全專案採用 Jetpack Compose 與 SwiftUI，具備流暢的互動體驗。

## 🛠️ 核心技術突破 (Android 穩定性與架構整合)
針對 OSMDroid 在 Compose 環境下的穩定性與 KMP 狀態同步，本專案成功解決以下挑戰：

* **跨平台資料源同步 (Single Source of Truth)**：為解決 Android 原生 View 與 Compose 狀態不同步的問題，將資料重心移至 `SharedViewModel`，透過 `StateFlow` 強制觸發 `AndroidView` 的 `update` 區塊，實現「一處修改，兩端同步」。
* **地圖互動 UI 優化 (Custom Marker Interaction)**：
    * 成功阻斷 OSMDroid 預設的 InfoWindow 彈窗，改以 Compose `AlertDialog` 實作自定義改名邏輯。
    * 實作「雙擊觸發」機制：點選標記進入導航模式，再次點選則開啟改名視窗，優化觸控體驗。
* **異步 UI 衝突修復 (WindowLeaked)**：解決了 `downloadAreaAsync` 背景下載時 Dialog 生命週期崩潰問題。透過自定義 `CacheManagerCallback` 與 `CoroutineScope` 實作靜默下載模式。
* **執行緒調度優化**：精確配置 `Dispatchers.Main` 與 `Dispatchers.IO` 的切換，確保下載任務與 UI 提示（Toast/Dialog）在正確執行緒運作。
* **高效能地圖渲染**：透過 `removeAll` 邏輯優化 `update` 區塊，避免 Compose 重複渲染導致的標記重疊與記憶體洩漏。

## 📱 互動式 GIS 成果展示
* **動態標籤渲染**：解析政府 Open Data 之風場區域 (GeoJSON)，實作半透明多邊形與標籤化渲染。
* **實時導航線繪製**：根據當前 GPS 位置與目標漁標，動態繪製 `Polyline` 並即時計算航行距離。
* **衛星圖資下載**：支援特定區域之衛星圖資預載，確保海上完全無網路環境下仍有視覺背景參考。

## 🏗️ 目前開發狀態
- [x] 跨平台 KMP 專案基礎環境架構與 Ktor 2.3.12 整合
- [x] **SharedViewModel 跨平台狀態同步邏輯 (StateFlow)**
- [x] OSMdroid 離線地圖底層渲染引擎 (Android)
- [x] **Android 端漁標自定義改名與持久化儲存 (MarkerStorage)**
- [x] Android 端衛星圖資異步下載與緩存機制 (穩定版)
- [x] 行政院政府 Open Data 風場資訊自動同步 (GeoJSON)
- [x] 實時方位校正與距離計算演算法 (Kotlin Shared Logic)
- [x] iOS 端 Apple Maps 整合與實時定位數據對接
- [⚠️] iOS 端離線地圖支援 (架構預留，MBTiles 整合開發中)

## 📄 授權與聲明 (License & Disclaimer)
* **版權所有**：© 2026 Ella Liu. All rights reserved.
* **僅供面試展示**：本專案程式碼與架構僅作為個人技術作品集與面試展示使用。