import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // 1. 不要直接在 ComposeView() 上用 ignoresSafeArea
        // 2. 改用 ZStack 或 GeometryReader 包裹，強迫 SwiftUI 重新計算觸控區域
        ZStack {
            ComposeView()
        }
        .ignoresSafeArea(.all, edges: .all) // 移到外層容器
    }
}



