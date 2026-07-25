import SwiftUI

/// 保活验证主界面
struct ContentView: View {

    @EnvironmentObject var manager: MonitorManager

    @State private var deviceId = ""
    @State private var filterSource: String? = nil  // 日志筛选：nil=全部

    // 被关心信息（模拟数据，后续接后端）
    @AppStorage("binder.name") private var binderName: String = "家人"
    @AppStorage("binder.bindDate") private var bindDateTimestamp: Double = Date().timeIntervalSince1970
    @AppStorage("binder.code") private var bindCode: String = ""

    private var caringDays: Int {
        let bindDate = Date(timeIntervalSince1970: bindDateTimestamp)
        let days = Calendar.current.dateComponents([.day], from: bindDate, to: Date()).day ?? 0
        return max(days, 0)
    }

    /// 获取或生成 6 位绑定码
    private func resolveBindCode() -> String {
        if bindCode.isEmpty {
            let code = String(format: "%06d", Int.random(in: 0...999999))
            bindCode = code
            return code
        }
        return bindCode
    }

    private let sources = ["SLC", "Motion", "BGAppRefresh", "Charging", "Foreground", "Alert"]

    private func sourceLabel(_ source: String) -> String {
        switch source {
        case "SLC":          return "位置唤醒"
        case "Motion":       return "运动感知"
        case "BGAppRefresh": return "后台刷新"
        case "Charging":     return "充电状态"
        case "Foreground":   return "前台切入"
        case "Alert":        return "⚠️ 告警"
        default:             return source
        }
    }

    private func appStateLabel(_ appState: String) -> String {
        switch appState {
        case "foreground": return "前台"
        case "background": return "后台"
        case "inactive":   return "非活跃"
        default:           return appState
        }
    }

    var body: some View {
        NavigationStack {
            List {
                // MARK: - 被关心卡片
                Section {
                    CareCard(binderName: binderName, caringDays: caringDays, bindCode: resolveBindCode())
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                // MARK: - 上报配置
                Section("📡 上报配置") {
                    TextField(
                        "云端地址（留空使用默认）",
                        text: $manager.baseURLString
                    )
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)

                    LabeledContent("设备 ID") {
                        Text(deviceId.isEmpty ? "加载中…" : deviceId)
                            .font(.caption.monospaced())
                            .foregroundStyle(.secondary)
                    }
                }

                // MARK: - 监测器开关
                Section("🎛️ 监测器开关") {
                    ForEach(sources, id: \.self) { source in
                        Toggle(sourceLabel(source), isOn: toggleBinding(for: source))
                    }
                }

                // MARK: - 启停按钮
                Section {
                    Button {
                        if manager.isRunning {
                            manager.stopAll()
                        } else {
                            manager.startAll()
                        }
                    } label: {
                        HStack {
                            Image(systemName: manager.isRunning ? "stop.circle.fill" : "play.circle.fill")
                            Text(manager.isRunning ? "停止监测" : "启动监测")
                        }
                        .frame(maxWidth: .infinity, alignment: .center)
                        .foregroundStyle(manager.isRunning ? .red : .green)
                        .fontWeight(.semibold)
                    }
                    .buttonStyle(.plain)
                }

                // MARK: - 唤醒统计
                Section("📊 唤醒统计") {
                    ForEach(sources, id: \.self) { source in
                        Button {
                            // 点击切换筛选：同一项再次点击取消筛选
                            filterSource = (filterSource == source) ? nil : source
                        } label: {
                            HStack {
                                Circle().fill(color(for: source)).frame(width: 8, height: 8)
                                Text(sourceLabel(source))
                                    .foregroundStyle(filterSource == source ? .primary : .secondary)
                                Spacer()
                                Text("\(manager.logger.stats[source] ?? 0)")
                                    .font(.body.monospaced())
                                    .foregroundStyle(.secondary)
                                if filterSource == source {
                                    Image(systemName: "line.horizontal.3.decrease.circle.fill")
                                        .foregroundStyle(.blue)
                                        .font(.caption)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }

                // MARK: - 唤醒日志
                Section(header: logSectionHeader) {
                    if filteredEntries.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "clock.badge.questionmark")
                                .font(.largeTitle)
                                .foregroundStyle(.secondary)
                            Text(filterSource == nil ? "暂无唤醒记录" : "该监测器暂无记录")
                                .foregroundStyle(.secondary)
                                .font(.callout)
                            Text("锁屏后移动手机、插拔电源等操作会触发唤醒事件，在此实时显示。")
                                .foregroundStyle(.tertiary)
                                .font(.caption)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 20)
                    }
                    ForEach(filteredEntries) { entry in
                        LogRow(entry: entry, sourceLabel: sourceLabel, appStateLabel: appStateLabel)
                    }
                }
            }
            .navigationTitle("安好 · 保活验证")
            .task {
                deviceId = await Reporter.shared.deviceId
            }
        }
    }

    // MARK: - Helpers

    /// 按 filterSource 筛选后的日志（nil = 全部）
    private var filteredEntries: [LogEntry] {
        guard let filter = filterSource else {
            return manager.logger.entries
        }
        return manager.logger.entries.filter { $0.source == filter }
    }

    /// 日志区域动态标题
    @ViewBuilder
    private var logSectionHeader: some View {
        if let filter = filterSource {
            HStack {
                Text("📋 \(sourceLabel(filter)) 日志")
                Spacer()
                Button("清除筛选") {
                    filterSource = nil
                }
                .font(.caption)
                .foregroundStyle(.blue)
            }
        } else {
            Text("📋 唤醒日志（最新在上）")
        }
    }

    private func toggleBinding(for source: String) -> Binding<Bool> {
        Binding(
            get: { manager.enabledMonitors.contains(source) },
            set: { isOn in
                if isOn {
                    manager.enabledMonitors.insert(source)
                } else {
                    manager.enabledMonitors.remove(source)
                }
            }
        )
    }

    private func color(for source: String) -> Color {
        switch source {
        case "SLC":          return .blue
        case "Motion":       return .green
        case "BGAppRefresh": return .orange
        case "Charging":     return .yellow
        case "Foreground":   return .purple
        case "Alert":        return .red
        default:             return .gray
        }
    }
}

// MARK: - 被关心卡片

/// 首页顶部「关心」卡片 —— 上方天数 + 下方数字绑定码
struct CareCard: View {
    let binderName: String
    let caringDays: Int
    let bindCode: String

    var body: some View {
        VStack(spacing: 0) {
            // --- 上半部：天数 ---
            VStack(spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: "heart.fill")
                        .font(.caption)
                    Text("您已被 \(binderName) 关心")
                        .font(.subheadline)
                }
                .foregroundStyle(.white.opacity(0.9))

                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("\(caringDays)")
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Text("天")
                        .font(.title3)
                        .foregroundStyle(.white.opacity(0.7))
                }

                Text(caringDays == 0 ? "今天刚绑定，守护刚刚开始" : "每一天，都有人在牵挂您")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.6))
            }
            .padding(.vertical, 20)

            // --- 分隔线 ---
            Rectangle()
                .fill(.white.opacity(0.25))
                .frame(height: 1)
                .padding(.horizontal, 32)

            // --- 下半部：数字码 ---
            VStack(spacing: 6) {
                Text("绑定码")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.6))

                Text(bindCode.map { String($0) }.joined(separator: " "))
                    .font(.system(size: 36, weight: .heavy, design: .monospaced))
                    .foregroundStyle(.white)

                Text("将此码展示给关心你的人完成绑定")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.6))
            }
            .padding(.vertical, 20)
        }
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(
                colors: [.orange, .pink],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .padding(.horizontal, 4)
    }
}

/// 单条日志行
struct LogRow: View {
    let entry: LogEntry
    let sourceLabel: (String) -> String
    let appStateLabel: (String) -> String

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text(sourceLabel(entry.source))
                .font(.caption2.monospaced())
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(backgroundColor.opacity(0.15))
                .foregroundStyle(backgroundColor)
                .clipShape(Capsule())
                .lineLimit(1)

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.event)
                    .font(.callout)
                    .lineLimit(2)

                HStack(spacing: 8) {
                    Text(entry.timestamp, format: .dateTime.hour().minute().second())
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    Text(appStateLabel(entry.appState))
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    Image(systemName: entry.reportedRemote ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(entry.reportedRemote ? .green : .gray)
                        .font(.caption2)
                }
            }
        }
        .padding(.vertical, 2)
    }

    private var backgroundColor: Color {
        switch entry.source {
        case "SLC":          return .blue
        case "Motion":       return .green
        case "BGAppRefresh": return .orange
        case "Charging":     return .yellow
        case "Foreground":   return .purple
        case "Alert":        return .red
        default:             return .gray
        }
    }
}
