import Foundation
import SwiftUI

/// 单条唤醒日志记录
struct LogEntry: Identifiable, Codable, Sendable, Hashable {
    let id: UUID
    let source: String          // 监测器标识：SLC / Motion / BGAppRefresh / Charging / Foreground
    let timestamp: Date
    let event: String           // 事件描述
    let appState: String        // 触发时 App 状态：foreground / background / inactive
    var reportedRemote: Bool    // 是否已成功远程上报

    init(source: String, event: String, appState: String, reportedRemote: Bool = false) {
        self.id = UUID()
        self.source = source
        self.timestamp = Date()
        self.event = event
        self.appState = appState
        self.reportedRemote = reportedRemote
    }
}

/// 本地日志记录器：内存 + UserDefaults 双写，App 被杀后可恢复历史
@MainActor
final class Logger: ObservableObject {

    @Published private(set) var entries: [LogEntry] = []
    @Published private(set) var stats: [String: Int] = [:]   // 按监测器统计唤醒次数

    private let maxEntries = 1000
    private let storageKey = "anhao.spike.logs.v1"

    init() {
        loadFromDisk()
    }

    /// 供 MonitorManager 在 MainActor 上下文调用，返回创建的 entry（含 id）
    func record(source: String, event: String, appState: String, reportedRemote: Bool) -> LogEntry {
        let entry = LogEntry(source: source, event: event, appState: appState, reportedRemote: reportedRemote)
        append(entry)
        return entry
    }

    /// 标记某条日志已成功远程上报（上报完成后回填）
    func markReported(id: UUID) {
        guard let idx = entries.firstIndex(where: { $0.id == id }) else { return }
        entries[idx].reportedRemote = true
        persist()
    }

    func clear() {
        entries.removeAll()
        stats.removeAll()
        UserDefaults.standard.removeObject(forKey: storageKey)
    }

    // MARK: - Internal

    private func append(_ entry: LogEntry) {
        entries.insert(entry, at: 0)
        if entries.count > maxEntries {
            entries = Array(entries.prefix(maxEntries))
        }
        stats[entry.source, default: 0] += 1
        persist()
    }

    private func loadFromDisk() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([LogEntry].self, from: data) else { return }
        entries = decoded
        stats = Dictionary(grouping: decoded, by: { $0.source }).mapValues { $0.count }
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }
}
