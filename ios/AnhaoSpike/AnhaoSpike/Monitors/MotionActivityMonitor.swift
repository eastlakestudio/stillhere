import CoreMotion

/// CoreMotion 活动监测器 —— 关键争议点
///
/// 验证目标：App 进入后台/锁屏后，CMMotionActivityManager 是否仍回调活动状态变化。
/// 文档未明确承诺后台回调，网上资料矛盾。本监测器专门实测。
///
/// 注意：需在 Info.plist 配置 UIBackgroundModes: motion（已配置）。
final class MotionActivityMonitor: Monitor, @unchecked Sendable {

    nonisolated let identifier = "Motion"

    nonisolated private let onWake: @Sendable (String, String) -> Void
    private let manager = CMMotionActivityManager()

    init(onWake: @escaping @Sendable (String, String) -> Void) {
        self.onWake = onWake
    }

    func start() {
        guard CMMotionActivityManager.isActivityAvailable() else {
            onWake(identifier, "activity unavailable on this device")
            return
        }
        if #available(iOS 17.0, *) {
            let auth = CMMotionActivityManager.authorizationStatus()
            if auth == .denied || auth == .restricted {
                onWake(identifier, "motion authorization denied")
                return
            }
        }
        // 用 .main 确保回调在主线程，避免跨 actor 访问 self 崩溃
        manager.startActivityUpdates(to: .main) { [weak self] activity in
            guard let self, let activity else { return }
            let desc = Self.describe(activity)
            let event = "activity: \(desc) (confidence: \(activity.confidence.rawValue))"
            self.onWake(self.identifier, event)
        }
    }

    func stop() {
        manager.stopActivityUpdates()
    }

    // MARK: - Helpers

    private static func describe(_ activity: CMMotionActivity) -> String {
        if activity.walking { return "walking" }
        if activity.running { return "running" }
        if activity.automotive { return "automotive" }
        if activity.cycling { return "cycling" }
        if activity.stationary { return "stationary" }
        return "unknown"
    }
}
