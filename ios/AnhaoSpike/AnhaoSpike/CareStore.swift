import Foundation
import SwiftUI

/// 一条关心关系
struct CareRelation: Codable, Identifiable, Equatable {
    var id: String          // 对方的 deviceId / 绑定码
    var name: String        // 昵称
    var bindCode: String    // 关心码（6位）
    var bindDate: Date      // 绑定日期
    var lastActive: Date?   // 最后活跃时间（从服务端同步）
    var isCharging: Bool = false

    /// 关心天数（从绑定日算起，最少 1 天）
    var days: Int {
        let d = Calendar.current.dateComponents([.day], from: bindDate, to: Date()).day ?? 0
        return max(d, 0) + 1
    }

    /// 活动状态文案
    var activityText: String {
        guard let ts = lastActive else { return "暂无活动" }
        let secondsAgo = Int(Date().timeIntervalSince(ts))
        switch secondsAgo {
        case ..<60:   return "刚刚活跃"
        case ..<3600: return "\(secondsAgo / 60) 分钟前活跃"
        case ..<86400: return "\(secondsAgo / 3600) 小时前活跃"
        default:      return "\(secondsAgo / 86400) 天前活跃"
        }
    }

    var isActive: Bool {
        guard let ts = lastActive else { return false }
        return Date().timeIntervalSince(ts) < 86400 // 24小时内算活跃
    }
}

/// 关心关系本地持久化管理
@MainActor
final class CareStore: ObservableObject {
    /// 我关心的人（关心列表）
    @Published var caring: [CareRelation] = []

    private let caringKey  = "anhao.care.caring"

    /// 被关心人数（由 MonitorManager 在心跳后更新）
    @Published var caredByCount: Int = 0

    /// 被关心天数（基于服务端返回人数计算）
    var totalCaredDays: Int {
        guard caredByCount > 0 else { return 0 }
        let earliest = caring.map(\.bindDate).min() ?? Date()
        let d = Calendar.current.dateComponents([.day], from: earliest, to: Date()).day ?? 0
        return max(d, 0) + 1
    }

    init() {
        load()
    }

    /// 从服务端恢复「我关心的人」（重装/换机后恢复；不覆盖已存在的本地关系）
    func syncFromServer() async {
        let devId = await Reporter.shared.deviceId
        let remote = await Reporter.shared.fetchCaring(deviceId: devId)
        guard !remote.isEmpty else { return }
        let existingCodes = Set(caring.map { $0.bindCode })
        let newOnes = remote.filter { !existingCodes.contains($0.bindCode) }
        guard !newOnes.isEmpty else { return }
        for r in newOnes {
            caring.append(CareRelation(
                id: UUID().uuidString,
                name: r.name.isEmpty ? r.bindCode : r.name,
                bindCode: r.bindCode,
                bindDate: Date()
            ))
        }
        save()
        print("[CareStore] synced \(newOnes.count) relations from server, total caring=\(caring.count)")
    }

    // MARK: - 我关心

    func addCaring(name: String, bindCode: String) {
        let rel = CareRelation(
            id: UUID().uuidString,
            name: name,
            bindCode: bindCode,
            bindDate: Date(),
            lastActive: nil
        )
        caring.append(rel)
        save()
        // 向服务端登记关心关系（不传昵称，隐私数据保留本地）
        Task {
            let ok = await Reporter.shared.registerCare(toCode: bindCode)
            print("[CareStore] registerCare result: \(ok)")
            // 添加后立即拉取状态
            await refreshCaredStatus()
        }
    }

    func removeCaring(_ relation: CareRelation) {
        caring.removeAll { $0.id == relation.id }
        save()
        // 同步删除服务端关心关系
        Task {
            let _ = await Reporter.shared.unregisterCare(toCode: relation.bindCode)
        }
    }

    func updateName(_ relation: CareRelation, name: String) {
        guard let idx = caring.firstIndex(where: { $0.id == relation.id }) else { return }
        caring[idx].name = name
        save()
    }

    // MARK: - 活动状态

    /// 拉取被关心者的最近活动状态
    func refreshCaredStatus() async {
        let codes = caring.map { $0.bindCode }
        guard !codes.isEmpty else { return }
        let statuses = await Reporter.shared.fetchCaredStatus(codes: codes)
        for i in caring.indices {
            let code = caring[i].bindCode
            if let s = statuses[code] {
                caring[i].lastActive = s.lastActive.map { Date(timeIntervalSince1970: TimeInterval($0)) }
                caring[i].isCharging = s.isCharging
            }
        }
    }

    // MARK: - 聚合

    var caringCount: Int { caring.count }

    var totalCaringDays: Int {
        guard let first = caring.map(\.bindDate).min() else { return 0 }
        let d = Calendar.current.dateComponents([.day], from: first, to: Date()).day ?? 0
        return max(d, 0) + 1
    }

    // MARK: - 持久化

    private func save() {
        guard let data = try? JSONEncoder().encode(caring),
              let json = String(data: data, encoding: .utf8) else { return }
        UserDefaults.standard.set(json, forKey: caringKey)
    }

    private func load() {
        guard let json = UserDefaults.standard.string(forKey: caringKey),
              let data = json.data(using: .utf8),
              let list = try? JSONDecoder().decode([CareRelation].self, from: data) else { return }
        caring = list
    }
}
