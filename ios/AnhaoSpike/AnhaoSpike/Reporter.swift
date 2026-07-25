import Foundation
import UIKit

/// 远程上报器：POST 到 Cloudflare Worker，记录心跳 + 上报设备状态
actor Reporter {

    static let shared = Reporter()

    /// Cloudflare Worker 默认地址
    private static let defaultWorkerURL = "https://stillhere-api.mingh-liu.workers.dev"

    /// 当前使用的 API 基地址（用户可在 UI 中自定义）
    private var baseURL: String

    /// 设备唯一标识，持久化（即 userId）
    private(set) var deviceId: String

    private init() {
        self.deviceId = Reporter.loadOrCreateDeviceId()
        // 从 UserDefaults 读取自定义地址，没有则用默认
        self.baseURL = UserDefaults.standard.string(forKey: "anhao.spike.baseURL") ?? Reporter.defaultWorkerURL
    }

    func setBaseURL(_ url: String?) {
        let newURL = (url?.isEmpty != false) ? Reporter.defaultWorkerURL : url!
        self.baseURL = newURL
        UserDefaults.standard.set(newURL, forKey: "anhao.spike.baseURL")
    }

    /// 上报唤醒事件（heartbeat）。返回是否成功。
    func report(source: String, event: String, appState: String, isCharging: Bool = false) async -> Bool {
        let url = URL(string: "\(baseURL)/heartbeat")!

        let body: [String: Any] = [
            "userId": deviceId,
            "isCharging": isCharging,
            "source": source,
            "event": event,
            "appState": appState,
            "timestamp": ISO8601DateFormatter().string(from: Date())
        ]

        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.timeoutInterval = 10

            let (_, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) {
                return true
            }
            return false
        } catch {
            return false
        }
    }

    // MARK: - Helpers

    private static func loadOrCreateDeviceId() -> String {
        let key = "anhao.spike.deviceId"
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        let new = UUID().uuidString
        UserDefaults.standard.set(new, forKey: key)
        return new
    }
}

/// App 状态检测工具（需在 MainActor 上下文调用）
@MainActor
enum AppState {
    static func current() -> String {
        switch UIApplication.shared.applicationState {
        case .active:     return "foreground"
        case .background: return "background"
        case .inactive:   return "inactive"
        @unknown default: return "unknown"
        }
    }
}
