import Foundation
import UIKit
import CryptoKit
import Security

/// 远程上报器：POST 到 Cloudflare Worker，记录心跳 + 上报设备状态
actor Reporter {

    static let shared = Reporter()

    /// Cloudflare Worker 默认地址
    private static let defaultWorkerURL = "https://api.padap.cn"

    /// 当前使用的 API 基地址（用户可在 UI 中自定义）
    private var baseURL: String

    /// 设备唯一标识，持久化（即 userId）
    private(set) var deviceId: String

    /// 关心码（基于 deviceId 做 SHA-256 派生，不可反推）
    var careCode: String { deviceId.toCareCode() }

    private init() {
        self.deviceId = Reporter.loadOrCreateDeviceId()
        let saved = UserDefaults.standard.string(forKey: "anhao.spike.baseURL")
        // 旧 URL（workers.dev 被 GFW 封禁）自动迁移到新地址
        if let saved, saved.contains("workers.dev") {
            UserDefaults.standard.removeObject(forKey: "anhao.spike.baseURL")
            self.baseURL = Reporter.defaultWorkerURL
            print("[Reporter] migrated baseURL from \(saved) to \(Reporter.defaultWorkerURL)")
        } else {
            self.baseURL = saved ?? Reporter.defaultWorkerURL
        }
    }

    func setBaseURL(_ url: String?) {
        let newURL = (url?.isEmpty != false) ? Reporter.defaultWorkerURL : url!
        self.baseURL = newURL
        UserDefaults.standard.set(newURL, forKey: "anhao.spike.baseURL")
    }

    /// 上报唤醒事件（heartbeat）。返回是否成功 + 被关心人数。
    func report(source: String, event: String, appState: String, isCharging: Bool = false) async -> (ok: Bool, caredByCount: Int) {
        let url = URL(string: "\(baseURL)/heartbeat")!

        let body: [String: Any] = [
            "userId": deviceId,
            "careCode": careCode,
            "isCharging": isCharging,
            "deviceToken": UserDefaults.standard.string(forKey: "anhao.spike.deviceToken") ?? "",
        ]

        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.timeoutInterval = 10

            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse {
                let body = String(data: data, encoding: .utf8) ?? ""
                print("[Reporter] HTTP \(http.statusCode): \(body)")
                if (200..<300).contains(http.statusCode) {
                    // 解析 caredByCount
                    var count = 0
                    if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                       let c = json["caredByCount"] as? Int {
                        count = c
                    }
                    return (true, count)
                }
            }
            return (false, 0)
        } catch {
            print("[Reporter] ERROR: \(error.localizedDescription)")
            return (false, 0)
        }
    }

    /// 向服务端登记一条关心关系（不传昵称，隐私数据保留本地）
    func registerCare(toCode: String) async -> Bool {
        let url = URL(string: "\(baseURL)/care")!
        let body: [String: Any] = [
            "fromUserId": deviceId,
            "toCode": toCode,
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
            print("[Reporter] registerCare ERROR: \(error.localizedDescription)")
            return false
        }
    }

    /// 从服务端删除一条关心关系
    func unregisterCare(toCode: String) async -> Bool {
        guard var components = URLComponents(string: "\(baseURL)/care") else { return false }
        components.queryItems = [
            URLQueryItem(name: "fromUserId", value: deviceId),
            URLQueryItem(name: "toCode", value: toCode),
        ]
        guard let url = components.url else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.timeoutInterval = 10
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) {
                return true
            }
            return false
        } catch {
            print("[Reporter] unregisterCare ERROR: \(error.localizedDescription)")
            return false
        }
    }

    // MARK: - 告警上报

    /// 上报空闲告警（被关心者 APP 检测到本地告警时调用）
    func reportAlert(idleMinutes: Int, isCharging: Bool = false) async -> Bool {
        return await postJSON("/alert", body: [
            "userId": deviceId,
            "careCode": careCode,
            "idleMinutes": idleMinutes,
            "isCharging": isCharging,
        ])
    }

    /// 取消告警（被关心者恢复活动时调用）
    func cancelAlert() async -> Bool {
        return await postJSON("/alert/cancel", body: [
            "userId": deviceId,
            "careCode": careCode,
        ])
    }

    /// 通用 POST JSON 请求
    private func postJSON(_ path: String, body: [String: Any]) async -> Bool {
        let url = URL(string: "\(baseURL)\(path)")!
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
            print("[Reporter] \(path) ERROR: \(error.localizedDescription)")
            return false
        }
    }

    /// 查询关心对象的最近活动状态
    func fetchCaredStatus(codes: [String]) async -> [String: RemoteCaredStatus] {
        let url = URL(string: "\(baseURL)/cared-status")!
        let body: [String: Any] = ["codes": codes]
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let codesData = json["codes"] as? [String: [String: Any]] else {
                return [:]
            }
            var result: [String: RemoteCaredStatus] = [:]
            for (code, info) in codesData {
                result[code] = RemoteCaredStatus(
                    lastActive: info["lastActive"] as? Int,
                    isCharging: info["isCharging"] as? Bool ?? false,
                    city: info["city"] as? String
                )
            }
            return result
        } catch {
            print("[Reporter] fetchCaredStatus ERROR: \(error.localizedDescription)")
            return [:]
        }
    }

    // MARK: - 关注我的人

    /// 查询关注自己的关心码列表
    func fetchCaredByMe(careCode: String) async -> [CarerInfo] {
        let url = URL(string: "\(baseURL)/cared-by-me?careCode=\(careCode)")!
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let carers = json["carers"] as? [[String: Any]] else {
                return []
            }
            return carers.compactMap { c in
                guard let code = c["careCode"] as? String else { return nil }
                return CarerInfo(careCode: code)
            }
        } catch {
            print("[Reporter] fetchCaredByMe ERROR: \(error.localizedDescription)")
            return []
        }
    }

    // MARK: - 问安

    /// 发送问安，返回问安记录 ID
    func sendGreeting(toCode: String, message: String = "问安") async -> Int64? {
        let url = URL(string: "\(baseURL)/greeting")!
        let body: [String: Any] = [
            "fromUserId": deviceId,
            "toCode": toCode,
            "message": message
        ]
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return nil
            }
            return (json["id"] as? NSNumber)?.int64Value
        } catch {
            print("[Reporter] sendGreeting ERROR: \(error.localizedDescription)")
            return nil
        }
    }

    /// 回复问安
    func replyGreeting(greetingId: Int64, reply: String) async -> Bool {
        let url = URL(string: "\(baseURL)/greeting/reply")!
        let body: [String: Any] = [
            "greetingId": greetingId,
            "reply": reply,
            "fromUserId": deviceId
        ]
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.timeoutInterval = 10
            let (_, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { return false }
            return (200..<300).contains(http.statusCode)
        } catch {
            print("[Reporter] replyGreeting ERROR: \(error.localizedDescription)")
            return false
        }
    }

    /// 拉取未回复的问安消息
    func fetchPendingGreetings(careCode: String) async -> [PendingGreeting] {
        let url = URL(string: "\(baseURL)/pending-greetings?careCode=\(careCode)")!
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let greetings = json["greetings"] as? [[String: Any]] else {
                return []
            }
            return greetings.compactMap { g in
                guard let id = g["id"] as? Int64,
                      let fromCareCode = g["fromCareCode"] as? String else { return nil }
                return PendingGreeting(
                    id: id,
                    fromCareCode: fromCareCode,
                    message: g["message"] as? String ?? "问安",
                    createdAt: g["createdAt"] as? Int64 ?? 0,
                    isReply: g["isReply"] as? Bool ?? false
                )
            }
        } catch {
            print("[Reporter] fetchPendingGreetings ERROR: \(error.localizedDescription)")
            return []
        }
    }

    // MARK: - Helpers

    private static func loadOrCreateDeviceId() -> String {
        let key = "anhao.spike.deviceId"

        if let existing = KeychainHelper.load(key: key) {
            return existing
        }

        let new = UUID().uuidString
        KeychainHelper.save(key: key, value: new)
        return new
    }
}

// MARK: - Keychain Helper

private enum KeychainHelper {
    private static let service = "com.eastlakestudio.stillhere"

    static func save(key: String, value: String) {
        guard let data = value.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    static func load(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
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

/// 远程活动状态（对应服务端 cared-status 返回）
struct RemoteCaredStatus {
    let lastActive: Int?      // Unix 秒
    let isCharging: Bool
    let city: String?
}

/// 关注我的人信息
struct CarerInfo {
    let careCode: String
}

/// 未回复的问安消息
struct PendingGreeting: Identifiable {
    let id: Int64
    let fromCareCode: String
    let message: String
    let createdAt: Int64     // Unix 秒
    let isReply: Bool        // true = 答复消息, false = 主动问安
}

// MARK: - 关心码哈希派生

extension String {
    /// SHA-256(self) → hex 第 8~13 位 → 6 位大写关心码，不可反推
    func toCareCode() -> String {
        let data = Data(self.utf8)
        let hash = SHA256.hash(data: data)
        let hex = hash.compactMap { String(format: "%02x", $0) }.joined()
        let start = hex.index(hex.startIndex, offsetBy: 8)
        let end = hex.index(start, offsetBy: 6)
        return String(hex[start..<end]).uppercased()
    }
}
