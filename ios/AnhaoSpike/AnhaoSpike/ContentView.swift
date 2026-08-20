import SwiftUI
import AVFoundation
import CoreImage.CIFilterBuiltins
import UserNotifications

/// 保活验证主界面
struct ContentView: View {

    @EnvironmentObject var manager: MonitorManager
    @EnvironmentObject var careStore: CareStore
    @Environment(\.scenePhase) private var scenePhase

    @State private var deviceId = ""
    @State private var filterSource: String? = nil
    @State private var showAddCare = false
    @State private var showBindCode = false
    @State private var selectedRelation: CareRelation? = nil

    // 关注我的人
    @State private var carers: [CarerInfo] = []
    @State private var showCarers = false

    // 问安
    @State private var pendingGreetings: [PendingGreeting] = []
    @State private var showGreetingReply = false
    @State private var greetingHistory: [GreetingHistoryItem] = []
    @State private var showGreetingHistory = false
    @State private var addCarePreFillCode: String? = nil
    @State private var toastMessage: String?
    @State private var toastTask: Task<Void, Never>?

    // 时段配置
    @State private var showTimeWindowConfig = false
    @State private var editingWindowIndex: Int?

    /// deviceId 经 SHA-256 哈希派生的 6 位大写关心码，不可反推
    private var shortBindCode: String {
        return deviceId.toCareCode()
    }

    private let sources = ["SLC", "Motion", "BGAppRefresh", "Charging", "Foreground", "Alert"]

    /// 时段配置概要
    private var timeWindowSummary: String {
        let windows = manager.monitoringWindows
        let count = windows.count
        let totalMinutes = windows.reduce(0) { total, tw in
            if tw.endMinutes > tw.startMinutes {
                return total + (tw.endMinutes - tw.startMinutes)
            } else {
                return total + (1440 - tw.startMinutes + tw.endMinutes)
            }
        }
        let hours = Double(totalMinutes) / 60.0
        let hStr = hours.truncatingRemainder(dividingBy: 1) == 0
            ? String(format: "%.0f", hours)
            : String(format: "%.1f", hours)
        return "守护 \(count) 个时段 · 共 \(hStr) 小时"
    }

    private func accentForWindow(_ idx: Int) -> Color {
        let colors: [Color] = [.blue, .purple, .orange, .green, .pink, .teal]
        return colors[idx % colors.count]
    }

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
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 12) {
                        // MARK: - 待确认告警横幅
                        if let idleMinutes = manager.pendingAlertMinutes {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(.orange)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("⚠️ 活动超时提醒")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                    Text("已 \(idleMinutes) 分钟无活动，若不取消将通知关心人")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Button("取消") {
                                    manager.cancelPendingAlert()
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(.orange)
                                .controlSize(.small)
                            }
                            .padding()
                            .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))
                        }

                        // MARK: - 被关心卡片（点击弹窗显示关心码）
                        CaredByCard(
                            count: careStore.caredByCount,
                            days: careStore.totalCaredDays,
                            onTap: { showBindCode = true },
                            showCarers: $showCarers,
                            carerCount: carers.count
                        )

                        // 关注我的人列表
                        if showCarers && !carers.isEmpty {
                            VStack(spacing: 8) {
                                HStack(spacing: 8) {
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(Color.blue.opacity(0.2))
                                        .frame(width: 3, height: 16)
                                    Text("关注你的人")
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                }

                                ForEach(carers, id: \.careCode) { carer in
                                    let caringRel = careStore.caring.first(where: { $0.bindCode == carer.careCode })
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(carer.careCode)
                                                .font(.body)
                                                .fontWeight(.semibold)
                                                .foregroundStyle(.blue)
                                            if let rel = caringRel {
                                                Text(rel.name)
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                            }
                                        }
                                        Spacer()
                                        if caringRel != nil {
                                            Text("已互相关心")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        } else {
                                            Button {
                                                addCarePreFillCode = carer.careCode
                                                showAddCare = true
                                            } label: {
                                                Image(systemName: "plus.circle.fill")
                                                    .font(.title3)
                                                    .foregroundStyle(.blue)
                                            }
                                        }
                                    }
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 10))
                                    .shadow(color: .black.opacity(0.03), radius: 4, y: 1)
                                }
                            }
                        }

                        // MARK: - 我关心
                        // 汇总行
                        HStack(spacing: 12) {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(Color.pink.opacity(0.15))
                                .frame(width: 4, height: 32)
                            Label("我关心", systemImage: "heart.fill")
                                .font(.headline)
                                .foregroundStyle(.pink)
                            Text("\(careStore.caringCount) 位")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            if careStore.caringCount > 0 {
                                Text("· \(careStore.totalCaringDays) 天")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button {
                                showAddCare = true
                            } label: {
                                Image(systemName: "plus.circle.fill")
                                    .font(.title3)
                                    .foregroundStyle(.blue)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 4)
                        .padding(.top, 8)

                        // 关心列表（最少 3 个卡片位）
                        let cardCount = max(careStore.caring.count, 3)
                        ForEach(0..<cardCount, id: \.self) { i in
                            if i < careStore.caring.count {
                                let rel = careStore.caring[i]
                                CaringRow(relation: rel,
                                    onGreeting: { code in
                                        Task {
                                            let id = await Reporter.shared.sendGreeting(toCode: code)
                                            if id != nil {
                                                showToast("已向「\(rel.name)」发送问安")
                                            }
                                        }
                                    }
                                )
                                    .onTapGesture { selectedRelation = rel }
                            } else {
                                EmptyCaringPlaceholder()
                            }
                        }
                    }
                    .padding(16)
                }

                // MARK: - 底部固定按钮区
                VStack(spacing: 10) {
                    // 时段配置入口（独立一行）
                     Button {
                        showTimeWindowConfig = true
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "clock.badge.checkmark")
                                .font(.subheadline)
                                .foregroundStyle(.blue)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(timeWindowSummary)
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                Text("上次活跃 \(manager.lastActivityText)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundStyle(.gray.opacity(0.4))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                    .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))

                    HStack(spacing: 10) {
                    // 报送平安（持续守护开关）
                    Button {
                        if manager.isRunning {
                            manager.stopAll()
                        } else {
                            manager.startAll()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(manager.isRunning ? Color.green : Color.gray.opacity(0.4))
                                .frame(width: 10, height: 10)
                            Text(manager.isRunning ? "守护中" : "报送平安")
                                .font(.subheadline)
                                .fontWeight(.medium)
                            Spacer()
                            Image(systemName: manager.isRunning ? "checkmark.circle.fill" : "circle")
                                .font(.callout)
                                .foregroundStyle(manager.isRunning ? .green : .secondary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                    .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))

                    // 系统配置
                    NavigationLink {
                        ConfigView(manager: manager, sources: sources,
                                   sourceLabel: sourceLabel, appStateLabel: appStateLabel,
                                   filterSource: $filterSource)
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "gearshape.fill")
                                .font(.subheadline)
                            Text("系统配置")
                                .font(.subheadline)
                                .fontWeight(.medium)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundStyle(.gray.opacity(0.4))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                    }
                    .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))
                }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
                .background(Color(.systemBackground))
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // 标题「晴好」居左（放大 1.5x）
                ToolbarItem(placement: .topBarLeading) {
                    Text("晴好")
                        .font(.system(size: 27, weight: .bold))
                }
                // 问安记录（标题栏最右侧，放大 1.5x）
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        greetingHistory = Reporter.shared.cachedGreetingHistory(careCode: shortBindCode)
                        showGreetingHistory = true
                    } label: {
                        Image(systemName: "message.fill")
                            .font(.system(size: 27))
                            .foregroundStyle(.blue)
                    }
                }
            }
            .task {
                deviceId = await Reporter.shared.deviceId
                manager.startAll()
                await careStore.refreshCaredStatus()
                // 请求通知权限
                _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            }
            .task(id: careStore.caredByCount) {
                if careStore.caredByCount > 0 {
                    carers = await Reporter.shared.fetchCaredByMe(careCode: shortBindCode)
                }
            }
            .task {
                // 收到问安推送：优先用 payload data 直接写本地缓存（不查服务器），无 data 才走 pending 兜底
                for await note in NotificationCenter.default.notifications(named: .greetingPushReceived) {
                    if let data = note.object as? [String: String], !data.isEmpty {
                        await Reporter.shared.saveGreetingFromPush(data: data, careCode: shortBindCode)
                        greetingHistory = await Reporter.shared.cachedGreetingHistory(careCode: shortBindCode)
                        showGreetingHistory = true
                    } else {
                        await fetchGreetings()
                    }
                }
            }
            .task {
                // 低频兜底轮询（防推送丢失）：前台 10 分钟，避免拖垮服务端
                try? await Task.sleep(nanoseconds: 10_000_000_000)
                while !Task.isCancelled {
                    await careStore.refreshCaredStatus()
                    await fetchGreetings()
                    try? await Task.sleep(nanoseconds: scenePhase == .active ? 600_000_000_000 : 3_600_000_000_000)
                }
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    Task { await fetchGreetings() }
                }
            }
            .sheet(isPresented: $showAddCare) {
                AddCareSheet(onAdd: { name, code in
                    careStore.addCaring(name: name, bindCode: code)
                    addCarePreFillCode = nil
                }, preFillCode: addCarePreFillCode)
            }
            .sheet(isPresented: $showBindCode) {
                BindCodeSheet(bindCode: shortBindCode, qrImage: generateQRCode(from: shortBindCode))
                    .presentationDetents([.medium, .large])
            }
            .sheet(item: $selectedRelation) { rel in
                CareDetailSheet(
                    relation: rel,
                    onUpdateName: { name in careStore.updateName(rel, name: name) },
                    onRemove: { careStore.removeCaring(rel) },
                    onGreeting: { code in
                        Task {
                            let id = await Reporter.shared.sendGreeting(toCode: code)
                            if id != nil {
                                showToast("已向「\(rel.name)」发送问安")
                            }
                        }
                    }
                )
            }
            .sheet(isPresented: $showGreetingReply) {
                let caringDict = Dictionary(uniqueKeysWithValues: careStore.caring.map { ($0.bindCode, $0.name) })
                GreetingReplySheet(
                    greetings: pendingGreetings,
                    caringDict: caringDict,
                    onReply: { greetingId, reply in
                        Task {
                            let ok = await Reporter.shared.replyGreeting(greetingId: greetingId, reply: reply)
                            if ok {
                                pendingGreetings = []
                                showGreetingReply = false
                            }
                        }
                    }
                )
                    .presentationDetents([.medium, .large])
            }
            .sheet(isPresented: $showGreetingHistory) {
                let caringDict = Dictionary(uniqueKeysWithValues: careStore.caring.map { ($0.bindCode, $0.name) })
                GreetingHistorySheet(history: greetingHistory, caringDict: caringDict)
                    .presentationDetents([.medium, .large])
            }
            .sheet(isPresented: $showTimeWindowConfig) {
                TimeWindowConfigSheet(manager: manager, editingWindowIndex: $editingWindowIndex)
            }
            .overlay(alignment: .top) {
                if let msg = toastMessage {
                    Text(msg)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
        }
    }

    // MARK: - Toast

    private func showToast(_ message: String) {
        toastTask?.cancel()
        withAnimation(.easeInOut(duration: 0.3)) {
            toastMessage = message
        }
        toastTask = Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            withAnimation(.easeInOut(duration: 0.3)) {
                toastMessage = nil
            }
        }
    }

    // MARK: - 问安拉取

    /// UserDefaults key: 上次已通知的最大 greeting id
    private static let lastNotifiedGreetingIdKey = "lastNotifiedGreetingId"

    private func fetchGreetings() async {
        let gs = await Reporter.shared.fetchPendingGreetings(careCode: shortBindCode)
        guard !gs.isEmpty, !showGreetingReply else { return }
        pendingGreetings = gs
        showGreetingReply = true

        // 只对新增的问安发本地通知（防重启重复通知）
        let lastId = UserDefaults.standard.object(forKey: Self.lastNotifiedGreetingIdKey) as? Int64 ?? 0
        let newGs = gs.filter { $0.id > lastId }
        if let maxId = gs.map(\.id).max() {
            UserDefaults.standard.set(maxId, forKey: Self.lastNotifiedGreetingIdKey)
        }
        guard !newGs.isEmpty else { return }

        let content = UNMutableNotificationContent()
        content.title = "问安消息"
        content.body = newGs.count == 1 ? "收到一条新的问安" : "收到 \(newGs.count) 条新的问安"
        content.sound = .default
        let req = UNNotificationRequest(identifier: "greeting-\(Date().timeIntervalSince1970)", content: content, trigger: nil)
        try? await UNUserNotificationCenter.current().add(req)
    }

    // MARK: - QR 码生成

    private func generateQRCode(from string: String) -> UIImage? {
        let data = Data(string.utf8)
        let filter = CIFilter.qrCodeGenerator()
        filter.message = data
        filter.correctionLevel = "M"

        guard let outputImage = filter.outputImage else { return nil }

        // 放大到清晰尺寸
        let scale: CGFloat = 10
        let transform = CGAffineTransform(scaleX: scale, y: scale)
        let scaled = outputImage.transformed(by: transform)

        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

// MARK: - 被关心卡片（精简版：点击弹出关心码）

struct CaredByCard: View {
    let count: Int
    let days: Int
    let onTap: () -> Void
    @Binding var showCarers: Bool
    let carerCount: Int

    var body: some View {
        HStack(spacing: 0) {
            // 左侧两行信息
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "heart.fill")
                        .font(.subheadline)
                        .foregroundStyle(.pink)
                    Text(count > 0 ? "被 \(count) 位家人关心" : "等待被关心")
                        .font(.body)
                        .foregroundStyle(.primary)
                }

                if count > 0 {
                    Text("每一天，都有人在牵挂您")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    Text("点击查看关心码，发给关心您的人")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            // 右侧：天数（占两行高度）
            if count > 0 {
                VStack(spacing: 2) {
                    Text("\(days)")
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .foregroundStyle(.primary)
                    Text("天")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            } else {
                Image(systemName: "qrcode")
                    .font(.title2)
                    .foregroundStyle(.secondary)
            }

            if carerCount > 0 {
                Button {
                    withAnimation { showCarers.toggle() }
                } label: {
                    Image(systemName: showCarers ? "chevron.up" : "chevron.down")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.leading, 8)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(
                colors: [Color.pink.opacity(0.10), Color.pink.opacity(0.04)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 16)
        )
        .shadow(color: .black.opacity(0.05), radius: 8, y: 3)
        .onTapGesture { onTap() }
    }
}

// MARK: - 关心码展示弹窗

struct BindCodeSheet: View {
    let bindCode: String
    let qrImage: UIImage?

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Spacer().frame(height: 20)

                Text("我的关心码")
                    .font(.title2)
                    .fontWeight(.bold)

                Spacer().frame(height: 24)

                // 关心码大字
                Text(bindCode.map { String($0) }.joined(separator: " "))
                    .font(.system(size: 42, weight: .heavy, design: .monospaced))
                    .foregroundStyle(.primary)
                    .kerning(4)

                Spacer().frame(height: 20)

                // 二维码
                if let qr = qrImage {
                    Image(uiImage: qr)
                        .resizable()
                        .interpolation(.none)
                        .frame(width: 200, height: 200)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(.gray.opacity(0.2), lineWidth: 1)
                        )
                }

                Spacer().frame(height: 20)

                Text("将此码展示给关心你的人完成绑定")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text("基于设备唯一标识，永久有效")
                    .font(.caption)
                    .foregroundStyle(.secondary.opacity(0.6))

                Spacer()
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
        }
    }
}

// MARK: - 关心行（卡片式）

struct CaringRow: View {
    let relation: CareRelation
    var onGreeting: ((String) -> Void)? = nil

    private var isActive: Bool { relation.isActive }

    private var accentColor: Color {
        isActive ? Color.green : Color.orange
    }

    var body: some View {
        HStack(spacing: 16) {
            // 头像
            ZStack {
                Circle()
                    .fill(accentColor.opacity(0.25))
                    .frame(width: 60, height: 60)
                Text(String(relation.name.prefix(2)))
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundStyle(accentColor)
            }

            VStack(alignment: .leading, spacing: 6) {
                // 第 1 行：昵称 + 关心天数
                HStack {
                    Text(relation.name)
                        .font(.title)
                        .fontWeight(.bold)
                    Spacer()
                    Text("\(relation.days) 天")
                        .font(.title2)
                        .fontWeight(.medium)
                        .foregroundStyle(.secondary)
                }

                // 第 2 行：活动状态
                HStack(spacing: 6) {
                    Circle()
                        .fill(accentColor)
                        .frame(width: 12, height: 12)
                    Text(relation.activityText)
                        .font(.title3)
                        .fontWeight(.medium)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            // 右侧操作区
            VStack(spacing: 4) {
                if let onGreeting {
                    Button {
                        onGreeting(relation.bindCode)
                    } label: {
                        Text("🙏")
                            .font(.title2)
                    }
                    .buttonStyle(.plain)
                }

                Image(systemName: "chevron.right")
                    .foregroundStyle(.gray.opacity(0.3))
                    .font(.caption)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .background(accentColor.opacity(isActive ? 0.18 : 0.14), in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
        .padding(.vertical, 2)
    }
}

// MARK: - 空占位卡片（用于预留关心人卡片位置）

struct EmptyCaringPlaceholder: View {
    var body: some View {
        HStack(spacing: 16) {
            // 头像占位
            Circle()
                .stroke(Color.gray.opacity(0.2), lineWidth: 1.5)
                .frame(width: 60, height: 60)
                .overlay {
                    Image(systemName: "person.fill")
                        .font(.title3)
                        .foregroundStyle(.gray.opacity(0.2))
                }

            VStack(alignment: .leading, spacing: 8) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.12))
                    .frame(width: 80, height: 20)
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.08))
                    .frame(width: 120, height: 18)
            }

            Spacer()

            Image(systemName: "plus")
                .font(.caption)
                .foregroundStyle(.gray.opacity(0.15))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.gray.opacity(0.12), style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
        )
        .padding(.vertical, 2)
    }
}

// MARK: - 添加关心 Sheet

struct AddCareSheet: View {
    let onAdd: (String, String) -> Void
    let preFillCode: String?

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var bindCode = ""
    @State private var showScanner = false

    var body: some View {
        NavigationStack {
            Form {
                Section("关心的人信息") {
                    TextField("昵称（如：爸爸、妈妈）", text: $name)

                    HStack(spacing: 8) {
                        TextField("关心码（6 位）", text: $bindCode)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .onChange(of: bindCode) { _, new in
                                bindCode = String(new.uppercased().prefix(6))
                            }

                        Button {
                            showScanner = true
                        } label: {
                            Image(systemName: "qrcode.viewfinder")
                                .font(.title3)
                                .foregroundStyle(.blue)
                        }
                        .buttonStyle(.plain)
                    }
                }

                Section {
                    Button {
                        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        let trimmedCode = bindCode.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !trimmedName.isEmpty, trimmedCode.count == 6 else { return }
                        onAdd(trimmedName, trimmedCode)
                        dismiss()
                    } label: {
                        HStack {
                            Spacer()
                            Label("添加关心", systemImage: "heart.fill")
                                .fontWeight(.semibold)
                            Spacer()
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || bindCode.count != 6)
                }
            }
            .navigationTitle("添加关心")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if let code = preFillCode {
                    bindCode = code
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            .sheet(isPresented: $showScanner) {
                QRScannerView { scannedCode in
                    bindCode = scannedCode
                    showScanner = false
                }
            }
        }
    }
}

// MARK: - 二维码扫描器

struct QRScannerView: UIViewControllerRepresentable {
    let onScan: (String) -> Void

    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> QRScannerController {
        let controller = QRScannerController()
        controller.onScan = { code in
            onScan(code)
        }
        controller.onCancel = {
            dismiss()
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: QRScannerController, context: Context) {}
}

@MainActor
final class QRScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onScan: ((String) -> Void)?
    var onCancel: (() -> Void)?

    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        // 设置导航栏
        navigationItem.title = "扫描关心码"
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .cancel, target: self, action: #selector(cancelTapped)
        )

        // 手电筒按钮
        if let device = AVCaptureDevice.default(for: .video), device.hasTorch {
            navigationItem.rightBarButtonItem = UIBarButtonItem(
                image: UIImage(systemName: "flashlight.off.fill"),
                style: .plain, target: self, action: #selector(toggleTorch)
            )
        }

        setupCamera()
    }

    @objc private func toggleTorch() {
        guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            if device.torchMode == .on {
                device.torchMode = .off
                navigationItem.rightBarButtonItem?.image = UIImage(systemName: "flashlight.off.fill")
            } else {
                try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
                navigationItem.rightBarButtonItem?.image = UIImage(systemName: "flashlight.on.fill")
            }
            device.unlockForConfiguration()
        } catch {
            print("Torch error: \(error)")
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // AVCaptureSession 非 Sendable；startRunning 在主线程执行（首次短暂阻塞可接受）
        session.startRunning()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        session.stopRunning()
        // 关闭手电筒
        if let device = AVCaptureDevice.default(for: .video), device.hasTorch, device.torchMode == .on {
            try? device.lockForConfiguration()
            device.torchMode = .off
            device.unlockForConfiguration()
        }
    }

    private func setupCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device) else {
            showError("无法访问相机")
            return
        }

        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.frame = view.bounds
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)

        // 扫描框
        let scanRect = UIView()
        scanRect.layer.borderColor = UIColor.white.cgColor
        scanRect.layer.borderWidth = 2
        scanRect.layer.cornerRadius = 12
        scanRect.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scanRect)
        NSLayoutConstraint.activate([
            scanRect.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            scanRect.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            scanRect.widthAnchor.constraint(equalToConstant: 220),
            scanRect.heightAnchor.constraint(equalToConstant: 220),
        ])

        // 提示文字
        let hint = UILabel()
        hint.text = "将关心码放入框内自动识别"
        hint.textColor = .white
        hint.font = .systemFont(ofSize: 14)
        hint.textAlignment = .center
        hint.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hint)
        NSLayoutConstraint.activate([
            hint.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            hint.topAnchor.constraint(equalTo: scanRect.bottomAnchor, constant: 24),
        ])
    }

    private func showError(_ message: String) {
        let alert = UIAlertController(title: "提示", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default) { [weak self] _ in
            self?.onCancel?()
        })
        present(alert, animated: true)
    }

    @objc private func cancelTapped() {
        onCancel?()
    }

    // MARK: - AVCaptureMetadataOutputObjectsDelegate

    nonisolated func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let code = obj.stringValue else { return }

        // 过滤：只取 6 位字母数字
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let filtered = String(trimmed.prefix(6))

        Task { @MainActor [filtered] in
            session.stopRunning()
            onScan?(filtered)
        }
    }
}

// MARK: - 系统配置页（导航进入）

struct ConfigView: View {
    @ObservedObject var manager: MonitorManager
    let sources: [String]
    let sourceLabel: (String) -> String
    let appStateLabel: (String) -> String
    @Binding var filterSource: String?
    @State private var logExpanded = false

    var body: some View {
        List {
            // 监测器开关
            Section("🎛️ 监测器开关") {
                HStack {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.caption)
                    Text(manager.lastReportStatus)
                        .font(.caption)
                }
                .foregroundStyle(.secondary)

                ForEach(sources, id: \.self) { source in
                    Toggle(sourceLabel(source), isOn: toggleBinding(for: source))
                }
            }

            // 唤醒统计
            Section("📊 唤醒统计") {
                ForEach(sources, id: \.self) { source in
                    Button {
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

            // 唤醒日志（可折叠）
            Section {
                if logExpanded || filterSource != nil {
                    if filteredEntries.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "clock.badge.questionmark")
                                .font(.largeTitle)
                                .foregroundStyle(.secondary)
                            Text(filterSource == nil ? "暂无唤醒记录" : "该监测器暂无记录")
                                .foregroundStyle(.secondary)
                                .font(.callout)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 20)
                    }
                    ForEach(filteredEntries) { entry in
                        LogRow(entry: entry, sourceLabel: sourceLabel, appStateLabel: appStateLabel)
                    }
                }
            } header: {
                HStack {
                    if let filter = filterSource {
                        Text("📋 \(sourceLabel(filter)) 日志")
                        Spacer()
                        Button("清除筛选") {
                            filterSource = nil
                        }
                        .font(.caption)
                        .foregroundStyle(.blue)
                    } else {
                        Button {
                            withAnimation { logExpanded.toggle() }
                        } label: {
                            HStack {
                                Text("📋 唤醒日志")
                                Spacer()
                                Text(filteredEntries.isEmpty ? "暂无记录" : "\(filteredEntries.count) 条记录")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Image(systemName: logExpanded ? "chevron.up" : "chevron.down")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .navigationTitle("系统配置")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var filteredEntries: [LogEntry] {
        guard let filter = filterSource else { return manager.logger.entries }
        return manager.logger.entries.filter { $0.source == filter }
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

// MARK: - 日志行

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

// MARK: - 关心人详情 Sheet

struct CareDetailSheet: View {
    let relation: CareRelation
    let onUpdateName: (String) -> Void
    let onRemove: () -> Void
    let onGreeting: ((String) -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var isEditing = false
    @State private var editName = ""
    @State private var showConfirm = false
    @State private var greetingSent = false

    private var isActive: Bool { relation.isActive }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 头像
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    (isActive ? Color(red: 0.30, green: 0.69, blue: 0.31) : Color(red: 1.0, green: 0.63, blue: 0.0)).opacity(0.55),
                                    (isActive ? Color(red: 0.30, green: 0.69, blue: 0.31) : Color(red: 1.0, green: 0.63, blue: 0.0)).opacity(0.25)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 72, height: 72)
                    Text(String(relation.name.prefix(2)))
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                }
                .padding(.top, 20)

                // 名字 — 可编辑
                if isEditing {
                    HStack(spacing: 8) {
                        TextField("昵称", text: $editName)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 200)
                        Button {
                            let trimmed = editName.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !trimmed.isEmpty {
                                onUpdateName(trimmed)
                                isEditing = false
                            }
                        } label: {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.title2)
                                .foregroundStyle(.green)
                        }
                        Button {
                            editName = relation.name
                            isEditing = false
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.top, 12)
                } else {
                    HStack(spacing: 8) {
                        Text(relation.name)
                            .font(.title2)
                            .fontWeight(.bold)
                        Button {
                            editName = relation.name
                            isEditing = true
                        } label: {
                            Image(systemName: "pencil.circle")
                                .font(.title3)
                                .foregroundStyle(.secondary.opacity(0.5))
                        }
                    }
                    .padding(.top, 12)
                }

                // 关心码
                Text("关心码 \(relation.bindCode)")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)

                // 状态信息卡片
                VStack(spacing: 12) {
                    HStack {
                        Circle()
                            .fill(isActive ? Color(red: 0.30, green: 0.69, blue: 0.31) : Color.gray.opacity(0.3))
                            .frame(width: 10, height: 10)
                        Text("活动状态")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(relation.activityText)
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }

                    Divider()

                    HStack {
                        Text(relation.isCharging ? "🔌" : "🔋")
                        Text("充电状态")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(relation.isCharging ? "正在充电" : "未充电")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }

                    Divider()

                    HStack {
                        Text("📅")
                        Text("关心天数")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(relation.days) 天")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                }
                .padding(16)
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, 24)
                .padding(.top, 20)

                Spacer()

                // 问安按钮
                if let onGreeting {
                    Button {
                        onGreeting(relation.bindCode)
                        greetingSent = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                            dismiss()
                        }
                    } label: {
                        HStack {
                            Image(systemName: "bell.fill")
                            Text("问安")
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.blue.opacity(0.15))
                    .foregroundStyle(.blue)
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
                }

                Button(role: .destructive) {
                    showConfirm = true
                } label: {
                    Text("移除关心")
                        .font(.caption)
                }
                .padding(.top, 20)
                .padding(.bottom, 32)
            }
            .navigationTitle("关心详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
            .alert("确认移除", isPresented: $showConfirm) {
                Button("取消", role: .cancel) { }
                Button("移除", role: .destructive) {
                    onRemove()
                    dismiss()
                }
            } message: {
                Text("确定要移除对「\(relation.name)」的关心吗？")
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - 问安回复弹窗

struct GreetingReplySheet: View {
    let greetings: [PendingGreeting]
    let caringDict: [String: String]  // careCode → nickname
    let onReply: (Int64, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var customReply = ""

    /// 去重：同一 fromCareCode 只保留 id 最大（最新）的那条
    private var deduped: [PendingGreeting] {
        Dictionary(grouping: greetings, by: { $0.fromCareCode })
            .compactMapValues { $0.max(by: { $0.id < $1.id }) }
            .values
            .sorted { $0.id > $1.id }  // 最新在前
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                ForEach(deduped) { g in
                    let label = caringDict[g.fromCareCode] ?? g.fromCareCode
                    let actionText = g.isReply ? "答复了你" : "向你问安"
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 4) {
                            Text(label)
                                .font(.body)
                                .fontWeight(.semibold)
                            Text(actionText)
                                .font(.body)
                                .foregroundStyle(g.isReply ? .purple : .primary)
                        }
                        if g.createdAt > 0 {
                            Text(Date(timeIntervalSince1970: TimeInterval(g.createdAt)), style: .date)
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                            + Text(" ")
                            + Text(Date(timeIntervalSince1970: TimeInterval(g.createdAt)), style: .time)
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        if !g.message.isEmpty {
                            Text("\"\(g.message)\"")
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }

                // 快捷回复：安好
                Button {
                    for g in deduped { onReply(g.id, "安好") }
                } label: {
                    HStack {
                        Image(systemName: "heart.fill")
                        Text("安好")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)
                .tint(.green)

                // 自定义回复
                HStack(spacing: 8) {
                    TextField("自定义回复", text: $customReply)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: .infinity)

                    Button {
                        let text = customReply.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !text.isEmpty {
                            for g in deduped { onReply(g.id, text) }
                            customReply = ""
                        }
                    } label: {
                        Image(systemName: "paperplane.fill")
                            .font(.title3)
                    }
                    .disabled(customReply.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding(.top, 8)
            }
            .padding(24)
            .navigationTitle("问安消息")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }
}

// MARK: - 问安历史弹窗

struct GreetingHistorySheet: View {
    let history: [GreetingHistoryItem]
    let caringDict: [String: String]  // careCode → nickname（本地「我关心的」昵称）

    @Environment(\.dismiss) private var dismiss

    private func timeStampText(_ ts: Int64) -> String {
        let d = Date(timeIntervalSince1970: TimeInterval(ts))
        return d.formatted(date: .numeric, time: .shortened)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    if history.isEmpty {
                        VStack(spacing: 8) {
                            Image(systemName: "tray")
                                .font(.largeTitle)
                                .foregroundStyle(.tertiary)
                            Text("还没有问安记录")
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.top, 60)
                    } else {
                        ForEach(history) { h in
                            GreetingHistoryRow(item: h, caringDict: caringDict, timeStampText: timeStampText)
                        }
                    }
                }
                .padding(20)
            }
            .navigationTitle("最近问安记录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
                // 标题栏右侧图标（右对齐）
                ToolbarItem(placement: .topBarTrailing) {
                    Image(systemName: "message.fill")
                        .foregroundStyle(.blue)
                        .font(.body)
                }
            }
        }
    }
}

private struct GreetingHistoryRow: View {
    let item: GreetingHistoryItem
    let caringDict: [String: String]  // careCode → nickname
    let timeStampText: (Int64) -> String

    private var isNew: Bool {
        item.reply == nil && !item.isReply
    }

    /// 展示名优先级：本地昵称（接收方起的）→ 服务端解析昵称 → 关心码
    private var displayLabel: String {
        if let nick = caringDict[item.fromCareCode], !nick.isEmpty { return nick }
        if !item.displayName.isEmpty, item.displayName != item.fromCareCode { return item.displayName }
        return item.fromCareCode
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text(displayLabel)
                    .font(.body)
                    .fontWeight(.semibold)
                Text(isNew ? "向你问安" : "答复了你")
                    .font(.body)
                    .foregroundStyle(isNew ? Color.primary : Color.purple)
            }
            Text(item.message)
                .font(.callout)
                .foregroundStyle(.secondary)
            if let reply = item.reply, !reply.isEmpty {
                HStack(spacing: 4) {
                    Image(systemName: "arrowshape.turn.up.left.fill")
                        .font(.caption2)
                        .foregroundStyle(.green)
                    Text("你回复：\(reply)")
                        .font(.footnote)
                        .foregroundStyle(.green)
                }
            }
            if item.createdAt > 0 {
                Text(timeStampText(item.createdAt))
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - 监测时段配置页（从主界面独立进入）

struct TimeWindowConfigSheet: View {
    @ObservedObject var manager: MonitorManager
    @Binding var editingWindowIndex: Int?
    @State private var showAddWindow = false
    @State private var idleSliderValue: Double

    @Environment(\.dismiss) private var dismiss

    init(manager: MonitorManager, editingWindowIndex: Binding<Int?>) {
        self.manager = manager
        self._editingWindowIndex = editingWindowIndex
        _idleSliderValue = State(initialValue: Double(manager.idleAlertMinutes))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // 说明
                    HStack {
                        Image(systemName: "info.circle.fill")
                            .foregroundStyle(.blue)
                            .font(.subheadline)
                        Text("仅在配置的时段内检测空闲告警，时段外不打扰")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Spacer()
                    }
                    .padding(.horizontal, 4)

                    // ── 时段列表卡片 ──
                    VStack(spacing: 0) {
                        HStack {
                            Text("监测时段")
                                .font(.title3)
                                .fontWeight(.semibold)
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                        .padding(.bottom, 12)

                        let windows = manager.monitoringWindows
                        ForEach(Array(windows.enumerated()), id: \.element.id) { idx, tw in
                            HStack(spacing: 12) {
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(accentForWindow(idx))
                                    .frame(width: 4, height: 36)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(tw.displayText)
                                        .font(.body)
                                        .fontWeight(.semibold)
                                    if !tw.label.isEmpty {
                                        Text(tw.label)
                                            .font(.caption)
                                            .foregroundStyle(accentForWindow(idx).opacity(0.8))
                                    }
                                }
                                Spacer()
                                HStack(spacing: 12) {
                                    Button {
                                        editingWindowIndex = idx
                                    } label: {
                                        Image(systemName: "pencil.circle.fill")
                                            .font(.title3)
                                            .foregroundStyle(.blue)
                                    }
                                    .buttonStyle(.plain)
                                    Button {
                                        var list = manager.monitoringWindows
                                        list.remove(at: idx)
                                        manager.monitoringWindows = list
                                    } label: {
                                        Image(systemName: "minus.circle.fill")
                                            .font(.title3)
                                            .foregroundStyle(.red.opacity(0.6))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 14)
                            .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))
                            .padding(.horizontal, 16)
                        }

                        // 添加按钮
                        Button {
                            showAddWindow = true
                        } label: {
                            HStack {
                                Image(systemName: "plus.circle.fill")
                                    .font(.body)
                                Text("添加监测时段")
                                    .font(.body)
                                    .fontWeight(.medium)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 32)
                        .padding(.top, 8)
                        .padding(.bottom, 20)
                    }
                    .background(Color(.systemGray6).opacity(0.5), in: RoundedRectangle(cornerRadius: 16))

                    // ── 空闲告警阈值 ──
                    VStack(spacing: 0) {
                        HStack {
                            Text("空闲告警阈值")
                                .font(.title3)
                                .fontWeight(.semibold)
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                        .padding(.bottom, 4)

                        VStack(alignment: .leading, spacing: 12) {
                            Text("\(Int(idleSliderValue)) 分钟无活动则告警")
                                .font(.body)
                                .fontWeight(.semibold)

                            Slider(value: $idleSliderValue, in: 60...120, step: 1) {
                                Text("阈值")
                            }
                            .tint(.blue)
                            .onChange(of: idleSliderValue) { _, newValue in
                                manager.idleAlertMinutes = Int(newValue)
                            }

                            HStack {
                                Text("5 分钟")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Spacer()
                                Text("120 分钟")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                        .background(Color(.systemGray6), in: RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, 16)
                        .padding(.bottom, 20)
                    }
                    .background(Color(.systemGray6).opacity(0.5), in: RoundedRectangle(cornerRadius: 16))
                }
                .padding(16)
            }
            .navigationTitle("守护时间段配置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
            .sheet(isPresented: $showAddWindow) {
                TimeWindowEditSheet(isNew: true) { tw in
                    var list = manager.monitoringWindows
                    list.append(tw)
                    manager.monitoringWindows = list
                }
            }
            .sheet(isPresented: Binding(
                get: { editingWindowIndex != nil },
                set: { if !$0 { editingWindowIndex = nil } }
            )) {
                if let idx = editingWindowIndex {
                    let windows = manager.monitoringWindows
                    if idx < windows.count {
                        TimeWindowEditSheet(
                            isNew: false,
                            initial: windows[idx]
                        ) { tw in
                            var list = manager.monitoringWindows
                            list[idx] = tw
                            manager.monitoringWindows = list
                            editingWindowIndex = nil
                        }
                    }
                }
            }
        }
    }

    private func accentForWindow(_ idx: Int) -> Color {
        let colors: [Color] = [.blue, .purple, .orange, .green, .pink, .teal]
        return colors[idx % colors.count]
    }
}

// MARK: - 监测时段编辑弹窗

struct TimeWindowEditSheet: View {
    let isNew: Bool
    var initial: TimeWindow = TimeWindow()
    let onSave: (TimeWindow) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var startHour: Int
    @State private var startMinute: Int
    @State private var durationHours: Double
    @State private var label: String

    init(isNew: Bool, initial: TimeWindow = TimeWindow(), onSave: @escaping (TimeWindow) -> Void) {
        self.isNew = isNew
        self.initial = initial
        self.onSave = onSave
        _startHour = State(initialValue: initial.startHour)
        _startMinute = State(initialValue: initial.startMinute)
        let start = initial.startMinutes
        let end = initial.endMinutes
        let dur = end > start ? end - start : (1440 - start + end)
        _durationHours = State(initialValue: Double(dur) / 60.0)
        _label = State(initialValue: initial.label)
    }

    private let hours = Array(0...23)
    private let minutes = [0, 15, 30, 45]

    /// 计算结束分钟数（跨日取模）
    private var computedEndMinutes: Int {
        (startHour * 60 + startMinute + Int(durationHours * 60)) % 1440
    }
    private var computedEndHour: Int { computedEndMinutes / 60 }
    private var computedEndMinute: Int { computedEndMinutes % 60 }
    private var isNextDay: Bool {
        (startHour * 60 + startMinute + Int(durationHours * 60)) >= 1440
    }

    private var durationDisplay: String {
        if durationHours >= 1.0 {
            let h = String(format: "%.1f", durationHours)
            return "\(h) 小时"
        } else {
            let m = Int(durationHours * 60)
            return "\(m) 分钟"
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("开始时间") {
                    HStack {
                        Picker("时", selection: $startHour) {
                            ForEach(hours, id: \.self) { h in
                                Text(String(format: "%02d", h)).tag(h)
                            }
                        }
                        .pickerStyle(.wheel)
                        .frame(maxWidth: .infinity)

                        Text(":")
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundStyle(.secondary)

                        Picker("分", selection: $startMinute) {
                            ForEach(minutes, id: \.self) { m in
                                Text(String(format: "%02d", m)).tag(m)
                            }
                        }
                        .pickerStyle(.wheel)
                        .frame(maxWidth: .infinity)
                    }
                }

                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("持续时长  \(durationDisplay)")
                            .font(.subheadline)
                            .fontWeight(.medium)

                        Slider(value: $durationHours, in: 0.5...24, step: 0.5) {
                            Text("持续时长")
                        }
                        .tint(.blue)

                        // 结束时间提示
                        HStack {
                            Text("预计结束")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("\(String(format: "%02d:%02d", computedEndHour, computedEndMinute))")
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundStyle(.secondary)
                            if isNextDay {
                                Text("（次日）")
                                    .font(.caption2)
                                    .foregroundStyle(.orange)
                            }
                            Spacer()
                        }
                    }
                }

                Section("标签（可选）") {
                    TextField("如：晨间、晚间、工作日", text: $label)
                }
            }
            .navigationTitle(isNew ? "添加监测时段" : "编辑监测时段")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        onSave(TimeWindow(
                            startHour: startHour, startMinute: startMinute,
                            endHour: computedEndHour, endMinute: computedEndMinute,
                            label: label.trimmingCharacters(in: .whitespaces)
                        ))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }
}
