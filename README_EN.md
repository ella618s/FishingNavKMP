# FishingNav KMP - Offline Marine Navigation System

[繁體中文版 (Traditional Chinese)] (./README.md)

This project is developed using the **Kotlin Multiplatform (KMP)** architecture, specifically designed for fishing vessels to navigate, record fishing markers, and monitor safety in network-blind marine environments.

## 🛠️ Technical Highlights
* **KMP Cross-Platform Architecture**: Achieves shared core logic between Android and iOS, including geographic coordinate calculations and data model management.
* **Responsive State Management (Compose + Flow)**: Utilizes `StateFlow` combined with `collectAsState` to achieve real-time synchronization between cross-platform logic and UI.
* **Cross-Platform Network Layer (Ktor 2.3.12)**: Unifies cross-platform API requests, paired with `kotlinx-serialization` for JSON parsing.
* **Offline Map Engine (OSMDroid)**: Deeply integrates the native OSMDroid framework on Android, supporting local caching and offline satellite tile rendering.
* **Modern UI Implementation**: Fully implemented with Jetpack Compose and SwiftUI, providing a smooth interactive user experience.

## 🛠️ Core Technical Breakthroughs

* **Single Source of Truth (SSOT)**: To resolve state desynchronization between Android native Views and Compose, the data layer is centralized in the `SharedViewModel`. It forces updates via `StateFlow` to achieve "modify once, sync on both platforms."
* **Automatic GPS Smart Route Detection**:
    * Implemented "instant detection on app launch" and "dynamic triggering upon location change."
    * Whenever the system fetches the initial GPS coordinates or detects location displacement, it automatically dispatches coordinates to the KMP `SharedViewModel` for algorithm evaluation, decoupled from map-click dependencies.
    * Driven by cross-platform `StateFlow` reactivity, enabling the UI layer to seamlessly toggle between Land Network Mode (`LAND`) and Ocean Straight-line Mode (`SEA`) in real time.
* **Custom Marker Interaction & UI Optimization**:
    * Blocked OSMDroid's default InfoWindow popup and replaced it with a custom rename dialog using Compose `AlertDialog`.
    * Implemented a "Double-Tap" mechanism: Single tap to enter navigation mode, double tap to open the rename window, optimizing touch interactions.
* **Asynchronous UI Conflict Resolution (WindowLeaked)**: Resolved lifecycle crash issues with Dialogs during `downloadAreaAsync` background downloads using a custom `CacheManagerCallback` and a dedicated `CoroutineScope` for silent downloads.
* **Cross-Platform Tile Pre-loading Algorithm**: Implemented the Mercator projection formula within the `SharedViewModel` to accurately convert latitude/longitude ranges into tile coordinates (X, Y) for asynchronous multi-threaded downloading via Ktor.
* **iOS Progress Listening & Type Mapping**:
    * Resolved the mismatch between Kotlin `StateFlow` and Swift `AsyncSequence` by wrapping a `watchProgress` listener on the Kotlin side to synchronize download progress.
    * Handled type mapping between `KotlinIntRange` and Swift `ClosedRange`, ensuring the iOS client accurately transmits download commands.
* **Thread Scheduling Optimization**: Configured precise context switching between `Dispatchers.Main` and `Dispatchers.IO` to ensure download tasks and UI notifications (Toasts/Dialogs) operate on correct threads.
* **High-Performance Map Rendering**: Optimized the map `update` block using `removeAll` logic, avoiding repeated Compose rendering that leads to marker overlapping and memory leaks.

## 📱 Interactive GIS Showcases
* **Dynamic Polygon & Label Rendering**: Parses government Open Data Wind Farm zones (GeoJSON) to implement semi-transparent polygon rendering and dynamic label mapping.
* **Real-time Navigation Line Generation**: Dynamically draws a `Polyline` based on the user's current GPS position and the target marker, instantly computing the sailing distance.
* **Satellite Tile Offloading**: Supports pre-loading satellite imagery for specified regions, ensuring a visual map reference even in total network-blind marine environments.

### Project Screenshots
| Android Satellite Navigation | iOS Map Mode |
| :---: | :---: |
| ![Android Screen](./screenshots/android_demo.png) | ![iOS Screen](./screenshots/ios_demo.png) |

## 🏗️ Development Status
- [x] Cross-platform KMP base architecture & Ktor 2.3.12 integration
- [x] **SharedViewModel state synchronization logic via StateFlow**
- [x] **Automatic GPS Smart Route Detection & Real-time Mode Switching (LAND/SEA)**
- [x] OSMdroid offline map rendering engine (Android)
- [x] **Custom marker renaming & persistence layer on Android (MarkerStorage)**
- [x] Asynchronous satellite map downloading & caching mechanism on Android
- [x] Automatic sync with Executive Yuan Open Data Wind Farm info (GeoJSON)
- [x] Real-time bearing calibration & distance calculation algorithms (Kotlin Shared Logic)
- [x] iOS Apple Maps integration & real-time location streaming
- [x] **iOS offline download UI (ProgressView) & logic binding**

## 📄 License & Disclaimer
* **Copyright**: © 2026 Ella Liu. All rights reserved.
* **For Interview Exhibition Only**: This project repository and architecture are strictly intended for personal portfolio display and job interview presentations.