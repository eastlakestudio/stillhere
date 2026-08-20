import SwiftUI
import UserNotifications

/// 收到问安推送时广播，触发界面拉取问安消息
extension Notification.Name {
    static let greetingPushReceived = Notification.Name("greetingPushReceived")
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        UserDefaults.standard.set(token, forKey: "anhao.spike.deviceToken")
        print("[APNs] device token: \(token)")
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[APNs] register failed: \(error.localizedDescription)")
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // 前台收到问安推送 → 广播触发拉取最新问安
        if notification.request.identifier.hasPrefix("greeting") || notification.request.content.categoryIdentifier.contains("greeting") {
            Task { @MainActor in
                NotificationCenter.default.post(name: .greetingPushReceived, object: nil)
            }
        }
        completionHandler([.banner, .sound, .badge])
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) async {
        // 点按/展示后触发拉取
        Task { @MainActor in
            NotificationCenter.default.post(name: .greetingPushReceived, object: nil)
        }
        completionHandler()
    }
}

@main
struct AnhaoSpikeApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
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
            if newPhase == .active {
                manager.reportForeground()
            }
        }
    }
}
