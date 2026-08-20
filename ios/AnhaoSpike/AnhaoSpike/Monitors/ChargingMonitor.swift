import UIKit

/// 充电状态监测器
///
/// 验证目标：插拔电源时（前台/后台）能否收到 batteryStateDidChangeNotification。
/// 这是补充信号，同时用于"充电豁免"逻辑的基础（后续 watchdog 用）。
final class ChargingMonitor: NSObject, Monitor, @unchecked Sendable {

    nonisolated let identifier = "Charging"

    nonisolated private let onWake: @Sendable (String, String) -> Void

    init(onWake: @escaping @Sendable (String, String) -> Void) {
        self.onWake = onWake
        super.init()
    }

    func start() {
        MainActor.assumeIsolated {
            UIDevice.current.isBatteryMonitoringEnabled = true
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(batteryStateChanged),
                name: UIDevice.batteryStateDidChangeNotification,
                object: nil
            )
            batteryStateChanged()
        }
    }

    func stop() {
        MainActor.assumeIsolated {
            NotificationCenter.default.removeObserver(
                self,
                name: UIDevice.batteryStateDidChangeNotification,
                object: nil
            )
            UIDevice.current.isBatteryMonitoringEnabled = false
        }
    }

    @objc private func batteryStateChanged() {
        MainActor.assumeIsolated {
            let state = UIDevice.current.batteryState
            let level = UIDevice.current.batteryLevel
            let levelPct = level >= 0 ? "\(Int(level * 100))%" : "?"
            let desc: String
            switch state {
            case .unknown:     desc = "unknown"
            case .unplugged:   desc = "unplugged"
            case .charging:    desc = "charging"
            case .full:        desc = "full"
            @unknown default:  desc = "unknown"
            }
            onWake(identifier, "battery: \(desc), level: \(levelPct)")
        }
    }
}
