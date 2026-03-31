# FishingNav KMP - 離線航海導航系統

本專案採用 **Kotlin Multiplatform (KMP)** 架構開發，專為漁船在海上無網路環境下的導航、漁標記錄與安全監控設計。

## 🛠️ 技術亮點
* **KMP 跨平台架構**：實現 Android/iOS 核心邏輯共享，包含地理座標計算與資料模型管理。
* **跨平台網路層 (Ktor 2.3.12)**：統一使用 Ktor 2.3.12 搭配 `kotlinx-serialization` 處理跨平台 API 請求，取代原生平台的 Retrofit 或 OkHttp。
* **離線地圖引擎 (OSMDroid)**：整合 OSMDroid 框架，支援本地快取與離線圖資渲染，確保海上導航不中斷。
* **精準定位系統**：串接 GPS/AGPS 並實作即時座標校正，優化海上弱訊號環境的定位表現。
* **現代化 UI 實作**：全專案採用 Jetpack Compose 與 SwiftUI 編寫，具備流暢的互動體驗與地圖圖層切換功能。

## 🛠️ 核心技術突破 (Android 穩定性與數據整合)
針對 OSMDroid 在 Compose 環境下的穩定性與政府 Open Data 串接，本專案成功解決以下挑戰：

* **異步 UI 衝突修復 (WindowLeaked)**：解決了原生 `downloadAreaAsync` 在背景下載時強行彈出 Dialog 導致的生命週期崩潰。透過自定義 `CacheManagerCallback` 實作靜默下載模式。
* **執行緒調度優化**：針對 `Handler` 初始化異常（`Can't create handler`），精確配置 `Dispatchers.Main` 與 `Dispatchers.IO` 的切換邏輯，確保下載任務與 UI 提示在正確執行緒運作。
* **複雜空間幾何解析**：處理 GeoJSON 中 `Polygon` 格式之多層巢狀 `coordinates` 結構，實現精確的風場區域邊界渲染。
* **API 相容性封裝**：手動適配新版 OSMDroid 介面，補齊 `setPossibleTilesInArea` 與多參數版 `updateProgress` 回調，解決編譯紅字問題。
* **高效能地圖渲染**：透過 `removeAll` 邏輯優化 `update` 區塊，避免 Compose 重複渲染導致的地圖記憶體洩漏與白格問題。

## 📱 互動式 GIS 成果展示
透過對 Open Data `properties` 欄位之解析，本專案實現了地理區域的中繼資料即時查詢：
* **動態標籤渲染**：針對特定風場區域，實作自動標籤化技術，提升航行者的環境感知能力。
* **點擊交互機制**：點擊圖層多邊形即可透過 `title` 屬性顯示該區域之詳細計畫名稱 (如：`wpname`)。

## 🏗️ 目前開發狀態
- [x] 跨平台 KMP 專案基礎環境架構建與 Ktor 2.3.12 整合
- [x] OSMdroid 離線地圖底層渲染引擎 (Android)
- [x] **Android 端衛星圖資異步下載與緩存機制 (穩定版)**
- [x] **行政院政府 Open Data 風場資訊自動同步 (GeoJSON)**
- [x] 實時方位校正與距離計算演算法 (Kotlin Shared Logic)
- [x] 自訂漁標點持久化儲存 (JSON 序列化)
- [x] Android 端漁標管理與交互邏輯實作
- [x] iOS 端 Apple Maps 整合與實時定位
- [x] iOS 與 Kotlin Shared 模組數據對接
- [ ] iOS 端離線地圖支援 (MBTiles 整合)

## 📄 授權與聲明 (License & Disclaimer)
* **版權所有**：© 2026 Ella Liu. All rights reserved.
* **僅供面試展示**：本專案程式碼與架構僅作為個人技術作品集與面試展示使用。
* **第三方資源**：地圖資料來源於 [OpenStreetMap](https://www.openstreetmap.org/)，政府資料來源於 [臺灣離岸風電地質與環境感知系統 API 服務供應平臺](https://windpower.geologycloud.tw/swagger/api-docs/api)。