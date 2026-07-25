import Combine
import Foundation
import SwiftUI

/// 监测器统一接口
protocol Monitor: AnyObject {
    var identifier: String { get }
    func start()
    func stop()
}

/// 监测器总管：管理生命周期 + 统一记录/上报流程
@MainActor
final class MonitorManager: ObservableObject, Sendable {

    let logger = Logger()

    @Published private(set) var isRunning = false

    /// 各监测器开关。逐个排查崩溃源：先只保留 Charging
    @Published var enabledMonitors: Set<String> = ["SLC", "Motion", "BGAppRefresh", "Charging"]

    /// Cloudflare Worker 基地址（用户在 UI 输入，留空则用默认值）
    @Published var baseURLString: String = "" {
        didSet { syncBaseURL() }
    }

    // 监测器实例
    private var slc: LocationSLCMonitor?
    private var motion: MotionActivityMonitor?
    private var bgRefresh: BackgroundRefreshMonitor?
    private var charging: ChargingMonitor?

    /// 最后活动时间（持久化，App 被杀后仍可判定）
    private var lastActivityTime: Date {
        get {
            let ts = UserDefaults.standard.double(forKey: "anhao.spike.lastActivity")
            return ts > 0 ? Date(timeIntervalSince1970: ts) : Date()
        }
        set {
            UserDefaults.standard.set(newValue.timeIntervalSince1970, forKey: "anhao.spike.lastActivity")
        }
    }
    /// 上次告警时间（持久化，避免跨启动重复告警）
    private var lastAlertTime: Date {
        get {
            let ts = UserDefaults.standard.double(forKey: "anhao.spike.lastAlert")
            return ts > 0 ? Date(timeIntervalSince1970: ts) : .distantPast
        }
        set {
            UserDefaults.standard.set(newValue.timeIntervalSince1970, forKey: "anhao.spike.lastAlert")
        }
    }

    private var cancellables = Set<AnyCancellable>()

    init() {
        // 启动时从 UserDefaults 恢复自定义 URL
        if let saved = UserDefaults.standard.string(forKey: "anhao.spike.baseURL"), !saved.isEmpty {
            baseURLString = saved
        }
        // 转发 logger 的 objectWillChange，确保 logger 更新时 UI 刷新
        logger.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    // MARK: - 生命周期

    func startAll() {
        guard !isRunning else { return }
        isRunning = true

        let wake: @Sendable (String, String) -> Void = { [weak self] source, event in
            self?.wake(source: source, event: event)
        }

        if enabledMonitors.contains("SLC") {
            slc = LocationSLCMonitor(onWake: wake)
            slc?.start()
        }
        if enabledMonitors.contains("Motion") {
            motion = MotionActivityMonitor(onWake: wake)
            motion?.start()
        }
        if enabledMonitors.contains("BGAppRefresh") {
            bgRefresh = BackgroundRefreshMonitor(onWake: wake)
            bgRefresh?.start()
        }
        if enabledMonitors.contains("Charging") {
            charging = ChargingMonitor(onWake: wake)
            charging?.start()
        }
        // Foreground 由 App scenePhase 触发，无独立监测器
    }

    func stopAll() {
        isRunning = false
        slc?.stop(); slc = nil
        motion?.stop(); motion = nil
        bgRefresh?.stop(); bgRefresh = nil
        charging?.stop(); charging = nil
    }

    /// 前台切入触发（由 App scenePhase 调用）
    func reportForeground() {
        wake(source: "Foreground", event: "app entered foreground")
        checkLocalAlert()
    }

    /// 检查是否需要生成本地告警（18:00-22:00，超过5分钟无活动）
    private func checkLocalAlert() {
        let now = Date()

        // 时段判断：每天 18:00 - 22:00
        let calendar = Calendar.current
        guard let hour = calendar.dateComponents([.hour], from: now).hour,
              hour >= 18, hour < 22 else {
            return
        }

        // 无活动超过 5 分钟
        let idleSeconds = now.timeIntervalSince(lastActivityTime)
        guard idleSeconds > 5 * 60 else { return }

        // 避免同一时段重复告警（5 分钟内不重复）
        guard now.timeIntervalSince(lastAlertTime) > 5 * 60 else { return }

        lastAlertTime = now
        let idleMinutes = Int(idleSeconds / 60)
        let event = "⚠️ 告警：已 \(idleMinutes) 分钟无活动"
        wake(source: "Alert", event: event)
    }

    // MARK: - 唤醒入口（可从任意线程调用）

    nonisolated func wake(source: String, event: String) {
        Task { @MainActor in
            self.handleWake(source: source, event: event)
        }
    }

    // MARK: - Internal

    private func handleWake(source: String, event: String) {
        lastActivityTime = Date()
        let appState = AppState.current()
        let entry = logger.record(source: source, event: event, appState: appState, reportedRemote: false)
        Task {
            let ok = await Reporter.shared.report(source: source, event: event, appState: appState)
            if ok {
                logger.markReported(id: entry.id)
            }
        }
    }

    private func syncBaseURL() {
        let trimmed = baseURLString.trimmingCharacters(in: .whitespacesAndNewlines)
        UserDefaults.standard.set(trimmed, forKey: "anhao.spike.baseURL")
        Task { await Reporter.shared.setBaseURL(trimmed.isEmpty ? nil : trimmed) }
    }
}
