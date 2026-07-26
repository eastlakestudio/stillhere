import SwiftUI

@main
struct AnhaoSpikeApp: App {
    @StateObject private var manager = MonitorManager()
    @StateObject private var careStore = CareStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(manager)
                .environmentObject(careStore)
                .onAppear {
                    manager.careStore = careStore
                }
        }
        .onChange(of: scenePhase) { _, newPhase in
            // 切回前台时触发 Foreground 事件（基线信号）
            if newPhase == .active {
                manager.reportForeground()
            }
        }
    }
}
