import Combine
import Foundation
import SwiftUI
import UserNotifications

// MARK: - 监测时段模型

/// 监测活动时段（与 Android TimeWindow 对齐）
struct TimeWindow: Codable, Equatable, Identifiable {
    var id: String { "\(startHour):\(startMinute)-\(endHour):\(endMinute)-\(label)" }
    var startHour: Int = 9
    var startMinute: Int = 0
    var endHour: Int = 18
    var endMinute: Int = 0
    var label: String = ""

    /// 开始时间转为分钟数 (0-1439)
    var startMinutes: Int { startHour * 60 + startMinute }
    /// 结束时间转为分钟数 (0-1439)
    var endMinutes: Int { endHour * 60 + endMinute }

    /// 格式化显示
    var displayText: String {
        let s = String(format: "%02d:%02d", startHour, startMinute)
        let e = String(format: "%02d:%02d", endHour, endMinute)
        return "\(s) – \(e)"
    }

    /// 检查给定分钟数是否在此窗口内
    func contains(minutes: Int) -> Bool {
        if endMinutes > startMinutes {
            return minutes >= startMinutes && minutes < endMinutes
        } else {
            // 跨日（如 22:00–06:00）
            return minutes >= startMinutes || minutes < endMinutes
        }
    }

    static let `default` = TimeWindow(startHour: 0, startMinute: 0, endHour: 23, endMinute: 59, label: "")
}

/// 监测器统一接口
protocol Monitor: AnyObject {
    var identifier: String { get }
    func start()
    func stop()
}

/// 监测器总管：管理生命周期 + 周期心跳 + 本地告警
@MainActor
final class MonitorManager: ObservableObject, Sendable {

    let logger = Logger()

    /// CareStore 引用（由 App 注入，用于更新心跳返回的 caredByCount）
    weak var careStore: CareStore?

    @Published private(set) var isRunning = false

    /// 最近一次上报状态（调试用）
    @Published private(set) var lastReportStatus: String = "等待上报…"

    /// 待确认告警：本机检测到告警后，5分钟内用户可取消，超时后才上报服务器
    @Published var pendingAlertMinutes: Int? = nil
    private var pendingAlertTimer: Timer?

    /// 各监测器开关
    @Published var enabledMonitors: Set<String> = ["SLC", "Motion", "BGAppRefresh", "Charging", "Foreground", "Alert"]

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

    /// 可读的最后活动文案（用于 UI 展示）
    var lastActivityText: String {
        let seconds = Date().timeIntervalSince(lastActivityTime)
        switch seconds {
        case ..<60:  return "刚刚活跃"
        case ..<120: return "1 分钟前"
        case ..<3600: return "\(Int(seconds / 60)) 分钟前"
        case ..<7200: return "1 小时前"
        case ..<86400: return "\(Int(seconds / 3600)) 小时前"
        default: return "\(Int(seconds / 86400)) 天前"
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

    /// 是否处于告警中（用于检测活动恢复时取消告警）
    private var isAlerted: Bool {
        get { UserDefaults.standard.bool(forKey: "anhao.spike.isAlerted") }
        set { UserDefaults.standard.set(newValue, forKey: "anhao.spike.isAlerted") }
    }

    /// 最后心跳时间（持久化）
    private var lastHeartbeatTime: Date {
        get {
            let ts = UserDefaults.standard.double(forKey: "anhao.spike.lastHeartbeat")
            return ts > 0 ? Date(timeIntervalSince1970: ts) : .distantPast
        }
        set {
            UserDefaults.standard.set(newValue.timeIntervalSince1970, forKey: "anhao.spike.lastHeartbeat")
        }
    }

    // MARK: - 可配置监测时段

    private static let keyWindows = "anhao.spike.monitoringWindows"
    private static let keyMigrated = "anhao.spike.migratedToWindows"

    /// 监测活动时段列表，仅在时段内才触发空闲告警
    var monitoringWindows: [TimeWindow] {
        get {
            migrateIfNeeded()
            guard let data = UserDefaults.standard.data(forKey: Self.keyWindows),
                  let decoded = try? JSONDecoder().decode([TimeWindow].self, from: data),
                  !decoded.isEmpty else {
                return [.default]
            }
            return decoded
        }
        set {
            if let data = try? JSONEncoder().encode(newValue) {
                UserDefaults.standard.set(data, forKey: Self.keyWindows)
            }
            syncConfigToCloud()
        }
    }

    /// 配置变更/启动时上传守护配置到服务端（供服务端按用户配置裁决空闲告警）
    func syncConfigToCloud() {
        let tzMinutes = TimeZone.current.secondsFromGMT() / 60
        var nicknames: [String: String] = [:]
        careStore?.caring.forEach { nicknames[$0.bindCode] = $0.name }
        let config: [String: Any] = [
            "monitoringWindows": monitoringWindows.map { [
                "startHour": $0.startHour,
                "startMinute": $0.startMinute,
                "endHour": $0.endHour,
                "endMinute": $0.endMinute,
                "label": $0.label,
            ] },
            "idleAlertMinutes": idleAlertMinutes,
            "timezoneOffsetMinutes": tzMinutes,
            "nicknames": nicknames,
        ]
        guard let json = try? JSONSerialization.data(withJSONObject: config) else { return }
        Task { await Reporter.shared.saveConfig(json: json) }
    }

    /// 旧数据迁移：wakeHour/sleepHour → monitoringWindows
    private func migrateIfNeeded() {
        if UserDefaults.standard.bool(forKey: Self.keyMigrated) { return }
        let wake = UserDefaults.standard.integer(forKey: "anhao.spike.wakeHour")
        let sleep = UserDefaults.standard.integer(forKey: "anhao.spike.sleepHour")
        if wake == 0 && sleep == 0 {
            UserDefaults.standard.set(true, forKey: Self.keyMigrated)
            return
        }
        var windows: [TimeWindow] = []
        let w = wake == 0 ? 7 : wake
        let s = sleep == 0 ? 22 : sleep
        if w < s {
            windows.append(TimeWindow(startHour: w, startMinute: 0, endHour: s, endMinute: 0, label: ""))
        } else {
            windows.append(.default)
        }
        if let data = try? JSONEncoder().encode(windows) {
            UserDefaults.standard.set(data, forKey: Self.keyWindows)
        }
        UserDefaults.standard.set(true, forKey: Self.keyMigrated)
        // 删除旧键
        UserDefaults.standard.removeObject(forKey: "anhao.spike.wakeHour")
        UserDefaults.standard.removeObject(forKey: "anhao.spike.sleepHour")
    }

    /// 检查当前时间是否在任一监测时段内
    func isInMonitoringWindow() -> Bool {
        let calendar = Calendar.current
        let now = Date()
        let hour = calendar.component(.hour, from: now)
        let minute = calendar.component(.minute, from: now)
        let minutes = hour * 60 + minute
        return monitoringWindows.contains { $0.contains(minutes: minutes) }
    }

    /// 空闲告警阈值（分钟），默认 30
    var idleAlertMinutes: Int {
        get { max(UserDefaults.standard.integer(forKey: "anhao.spike.idleAlertMinutes"), 5) }
        set {
            UserDefaults.standard.set(newValue, forKey: "anhao.spike.idleAlertMinutes")
            syncConfigToCloud()
        }
    }

    /// 防止并发心跳
    private var isSendingHeartbeat = false


    /// 周期心跳计时器
    private var heartbeatTimer: Timer?

    /// 当前充电状态
    private var isCharging: Bool {
        let state = UIDevice.current.batteryState
        return state == .charging || state == .full
    }

    private var cancellables = Set<AnyCancellable>()

    init() {
        // 默认值
        if UserDefaults.standard.integer(forKey: "anhao.spike.idleAlertMinutes") == 0 {
            idleAlertMinutes = 30
        }

        // 启动时从 UserDefaults 恢复自定义 URL
        if let saved = UserDefaults.standard.string(forKey: "anhao.spike.baseURL"), !saved.isEmpty {
            baseURLString = saved
        }
        // 转发 logger 的 objectWillChange
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

        // 启动周期心跳
        startPeriodicHeartbeat()

        // 启动时立即发送心跳（让关心人看到「刚刚活跃」、刷新被关心人数）
        sendHeartbeatNow()

        // 上传守护配置到服务端（供服务端裁决空闲告警）
        syncConfigToCloud()
    }

    func stopAll() {
        isRunning = false
        slc?.stop(); slc = nil
        motion?.stop(); motion = nil
        bgRefresh?.stop(); bgRefresh = nil
        charging?.stop(); charging = nil
        stopPeriodicHeartbeat()
    }

    // MARK: - 周期心跳（约每小时一次）

    private func startPeriodicHeartbeat() {
        heartbeatTimer?.invalidate()
        // 每分钟检查一次是否到了心跳间隔
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.checkAndSendHeartbeat()
            }
        }
    }

    private func stopPeriodicHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    private func checkAndSendHeartbeat() {
        let elapsed = Date().timeIntervalSince(lastHeartbeatTime)
        if elapsed >= 3600 { // 1 小时
            sendHeartbeat()
        }
    }

    /// 立即发送心跳（前台切入时调用）
    func sendHeartbeatNow() {
        Task { @MainActor in
            sendHeartbeat()
        }
    }

    private func sendHeartbeat() {
        guard !isSendingHeartbeat else { return }
        isSendingHeartbeat = true

        Task {
            let result = await Reporter.shared.report(source: "Periodic", event: "heartbeat", appState: AppState.current(), isCharging: isCharging)
            let timeStr = Date().formatted(.dateTime.hour().minute().second())
            if result.ok {
                lastHeartbeatTime = Date()
                lastReportStatus = "✅ 心跳 \(timeStr)"
                careStore?.caredByCount = result.caredByCount
            } else {
                lastReportStatus = "❌ 心跳失败 \(timeStr)"
            }
            isSendingHeartbeat = false
            print("[MonitorManager] heartbeat result: \(result.ok), caredBy: \(result.caredByCount)")
        }
    }

    // MARK: - 前台切入

func reportForeground() {
        wake(source: "Foreground", event: "app entered foreground")
        // 清除角标（App 已打开，无需提示"）
        Task {
            try? await UNUserNotificationCenter.current().setBadgeCount(0)
        }
        // 前台切入立即发送心跳（让关心人看到"刚刚活跃"）
        sendHeartbeatNow()
        // 检查本地告警
        checkLocalAlert()
    }

    /// 检查是否需要生成本地告警（基于用户可配置时段）
    private func checkLocalAlert() {
        // 检查是否在监测时段内
        guard isInMonitoringWindow() else { return }

        let now = Date()

        // 空闲超过阈值
        let idleSeconds = now.timeIntervalSince(lastActivityTime)
        guard idleSeconds > Double(idleAlertMinutes * 60) else { return }

        // 避免同一时段重复告警（5 分钟内不重复）
        guard now.timeIntervalSince(lastAlertTime) > 5 * 60 else { return }

        lastAlertTime = now
        isAlerted = true
        let idleMinutes = Int(idleSeconds / 60)
        let event = "⚠️ 告警：已 \(idleMinutes) 分钟无活动"
        wake(source: "Alert", event: event)

        // 显示本地通知
        let content = UNMutableNotificationContent()
        content.title = "晴好 · 活动超时提醒"
        content.body = "已 \(idleMinutes) 分钟无活动，5分钟内未取消将通知关心人"
        content.sound = .default
        let req = UNNotificationRequest(identifier: "alert-\(Date().timeIntervalSince1970)", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)

        // 设置 5 分钟延迟计时器，超时后才上报服务器
        pendingAlertMinutes = idleMinutes
        pendingAlertTimer?.invalidate()
        pendingAlertTimer = Timer.scheduledTimer(withTimeInterval: 5 * 60, repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.firePendingAlert()
            }
        }

        // 立即上报心跳，让关心人看到异常状态
        sendHeartbeatNow()
    }

    /// 超时后执行：上报告警到服务器
    private func firePendingAlert() {
        guard let idleMinutes = pendingAlertMinutes else { return }
        pendingAlertMinutes = nil
        pendingAlertTimer?.invalidate()
        pendingAlertTimer = nil
        Task {
            let ok = await Reporter.shared.reportAlert(idleMinutes: idleMinutes, isCharging: isCharging)
            print("[MonitorManager] firePendingAlert result: \(ok)")
        }
    }

    /// 用户取消待确认告警
    func cancelPendingAlert() {
        guard pendingAlertMinutes != nil else { return }
        pendingAlertMinutes = nil
        pendingAlertTimer?.invalidate()
        pendingAlertTimer = nil
        lastAlertTime = .distantPast  // 允许再次触发
        isAlerted = false
        Task {
            let ok = await Reporter.shared.cancelAlert()
            print("[MonitorManager] cancelPendingAlert result: \(ok)")
        }
        // 移除通知
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }

    // MARK: - 唤醒入口（可从任意线程调用）

    nonisolated func wake(source: String, event: String) {
        Task { @MainActor in
            self.handleWake(source: source, event: event)
        }
    }

    // MARK: - Internal

    /// 唤醒处理：仅记录活动时间 + 日志，不再每次事件都上报心跳
    private func handleWake(source: String, event: String) {
        lastActivityTime = Date()
        let appState = AppState.current()
        let entry = logger.record(source: source, event: event, appState: appState, reportedRemote: true)
        print("[MonitorManager] wake source=\(source) event=\(event)")

        // 如果之前处于告警状态 → 活动恢复，取消告警
        if isAlerted {
            isAlerted = false
            lastAlertTime = .distantPast
            Task {
                let ok = await Reporter.shared.cancelAlert()
                print("[MonitorManager] cancelAlert result: \(ok)")
            }
        }

        // 后台检测到活动时，若距上次心跳超过 2 分钟，发送心跳更新服务端状态
        let elapsed = Date().timeIntervalSince(lastHeartbeatTime)
        if elapsed >= 120 {
            sendHeartbeat()
        }

        // 日志标记为"已上报"（默认状态，实际心跳由周期任务负责）
        logger.markReported(id: entry.id)
    }

    private func syncBaseURL() {
        let trimmed = baseURLString.trimmingCharacters(in: .whitespacesAndNewlines)
        UserDefaults.standard.set(trimmed, forKey: "anhao.spike.baseURL")
        Task { await Reporter.shared.setBaseURL(trimmed.isEmpty ? nil : trimmed) }
    }
}
