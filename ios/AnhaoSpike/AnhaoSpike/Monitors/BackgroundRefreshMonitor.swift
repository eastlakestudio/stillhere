import BackgroundTasks

/// BGAppRefreshTask 兜底监测器
///
/// 验证目标：iOS 后台应用刷新调度频率与可靠性。系统决定调度时机，
/// 不可控，但作为兜底信号仍有价值。
///
/// 注意：BGTaskScheduler.register 只能调用一次，重复注册会崩溃。
/// 用 static flag 保证只注册一次，后续 start/stop 仅控制调度请求。
final class BackgroundRefreshMonitor: Monitor, @unchecked Sendable {

    nonisolated let identifier = "BGAppRefresh"
    static let taskIdentifier = "com.eastlakestudio.stillhere.refresh"

    nonisolated private let onWake: @Sendable (String, String) -> Void
    // MonitorManager.startAll 在 @MainActor 上下文调用 start()，实际单线程访问
    nonisolated(unsafe) private static var didRegister = false

    init(onWake: @escaping @Sendable (String, String) -> Void) {
        self.onWake = onWake
    }

    func start() {
        // register 全局只做一次
        if !Self.didRegister {
            BGTaskScheduler.shared.register(
                forTaskWithIdentifier: Self.taskIdentifier,
                using: nil
            ) { [weak self] task in
                guard let self, let refreshTask = task as? BGAppRefreshTask else {
                    task.setTaskCompleted(success: false)
                    return
                }
                // BGTaskScheduler handler 在后台线程回调。onWake 为 nonisolated @Sendable 可安全调用
                refreshTask.expirationHandler = { refreshTask.setTaskCompleted(success: false) }
                self.onWake(self.identifier, "BGAppRefresh fired")
                refreshTask.setTaskCompleted(success: true)
                self.scheduleNext()
            }
            Self.didRegister = true
        }
        scheduleNext()
    }

    func stop() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)
    }

    // MARK: - Internal

    private func scheduleNext() {
        let request = BGAppRefreshTaskRequest(identifier: Self.taskIdentifier)
        // 最早 15 分钟后被调度（实际时机由系统决定，可能更晚）
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            onWake(identifier, "schedule failed: \(error.localizedDescription)")
        }
    }
}
