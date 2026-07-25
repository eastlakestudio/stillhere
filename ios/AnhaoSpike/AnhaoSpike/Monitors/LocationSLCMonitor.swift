import CoreLocation

/// 显著位置变化监测器（SLC）—— 主信号
///
/// 关键特性：即使 App 被系统杀掉，基站切换（约 500m 移动）也会唤醒 App。
/// 这是整个保活范式的基石，本监测器专门验证其可靠性。
final class LocationSLCMonitor: NSObject, Monitor, CLLocationManagerDelegate, @unchecked Sendable {

    nonisolated let identifier = "SLC"

    nonisolated private let onWake: @Sendable (String, String) -> Void
    private let manager = CLLocationManager()

    init(onWake: @escaping @Sendable (String, String) -> Void) {
        self.onWake = onWake
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.pausesLocationUpdatesAutomatically = false
        // allowsBackgroundLocationUpdates 在获得 always 授权后设置
    }

    func start() {
        let status = manager.authorizationStatus
        switch status {
        case .authorizedAlways:
            manager.allowsBackgroundLocationUpdates = true
            manager.startMonitoringSignificantLocationChanges()
            onWake(identifier, "SLC started (already authorized)")
        case .notDetermined:
            // 首次请求，会弹出系统权限弹窗。授权后 delegate 回调启动监测
            manager.requestAlwaysAuthorization()
            onWake(identifier, "requesting Always authorization…")
        case .authorizedWhenInUse:
            // 已有使用时权限，升级到始终
            manager.requestAlwaysAuthorization()
            onWake(identifier, "upgrading to Always authorization…")
        case .denied, .restricted:
            onWake(identifier, "location denied/restricted — SLC unavailable")
        @unknown default:
            break
        }
    }

    func stop() {
        manager.stopMonitoringSignificantLocationChanges()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // nonisolated 方法，只上报事件。用户在 start() 中已 requestAlwaysAuthorization
        onWake(identifier, "authorization changed")
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let coord = locations.last?.coordinate
        let lat = coord.map { String(format: "%.4f", $0.latitude) } ?? "?"
        let lon = coord.map { String(format: "%.4f", $0.longitude) } ?? "?"
        onWake(identifier, "significant location change (\(lat), \(lon))")
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        onWake(identifier, "error: \(error.localizedDescription)")
    }
}
