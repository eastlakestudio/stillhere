这是一份为 “还在” (Still Here) 独居安全监控 App 量身定制的全系统开发指南。
整套方案采用 iOS App (Swift) + Laf (laf.run 云开发) + APNs (苹果推送) 架构。零独立服务器成本，全套逻辑均可在免费或极低成本下跑通。
目录结构
1. 第一部分：APNs 推送密钥申请
2. 第二部分：Laf (laf.run) 后端开发（数据库 + 云函数）
3. 第三部分：iOS App 端核心代码实现（Swift）
4. 第四部分：系统联调与部署说明
第一部分：APNs 推送密钥申请
要实现云端给关注人发通知，必须获取苹果的 .p8 推送密钥：
1. 登录 Apple Developer 开发者后台。
2. 创建 App ID： • 进入 Certificates, Identifiers & Profiles -> Identifiers。 • 新建一个 App ID（例如 com.yourname.stillhere）。 • 勾选 Capabilities 中的 Push Notifications。
3. 生成 APNs .p8 密钥： • 进入 Keys -> 点击 + 新建 Key。 • 勾选 Apple Push Notifications service (APNs)。 • 点击 Continue -> Register，下载 .p8 文件（⚠️ 注意：此文件只能下载一次，请妥善保存）。 • 记录下控制台上的 Key ID（10位字符）以及你的 Team ID（开发者账号右上角可查）。
第二部分：Laf (laf.run) 后端搭建与代码
Laf 是国内免备案、开箱即用的 Serverless 平台。
1. 创建 Laf 应用与数据库集合
1. 打开 laf.run 注册账号并创建一个应用（选择 Node.js 环境）。
2. 在左侧菜单进入 数据库，创建 2 个集合（Collection）： • users：存储用户、设备 Token、最后活跃时间及关联人。 • bind_codes：存储 5 分钟内有效的 6 位绑定数字码/二维码 Token。
2. 配置环境变量与依赖
在 Laf 应用设置的 环境变量 中添加：
• APNS_KEY_ID: 你的 10 位 Key ID
• APNS_TEAM_ID: 你的 10 位 Team ID
• APP_BUNDLE_ID: 你的 App 包名（如 com.yourname.stillhere）
• APNS_P8_KEY: .p8 文件中的纯文本内容（包含 -----BEGIN PRIVATE KEY-----）
在 Laf 左下角的 依赖管理 中添加 npm 包：
• apn (用于调用苹果推送服务)
3. Laf 云函数代码
在 Laf 控制台点击“云函数”，分别新建以下 4 个云函数：
函数 1：generate-bind-code（生成绑定码/二维码 Token）
import cloud from '@lafjs/cloud'

const db = cloud.database()

export default async function (ctx: FunctionContext) {
  const { userId } = ctx.body
  if (!userId) return { code: 400, message: '缺少 userId' }

  // 生成 5 分钟内有效的 6 位纯数字验证码
  const code = Math.floor(100000 + Math.random() * 900000).toString()
  const now = Date.now()
  const expiresAt = now + 5 * 60 * 1000 // 5 分钟后过期

  // 清除该用户历史未使用的配对码
  await db.collection('bind_codes').where({ userId }).remove()

  // 写入数据库
  await db.collection('bind_codes').add({
    code,
    userId,
    createdAt: now,
    expiresAt
  })

  // 二维码内容可直接使用 code，或拼接 JSON 字符串
  return {
    code: 0,
    data: {
      bindCode: code,
      qrContent: JSON.stringify({ action: "bind", code: code, userId: userId }),
      expiresAt: expiresAt
    }
  }
}

函数 2：bind-user（处理扫码/数字码绑定）
import cloud from '@lafjs/cloud'

const db = cloud.database()

export default async function (ctx: FunctionContext) {
  const { followerId, bindCode } = ctx.body // followerId: 关注人的 userId，bindCode: 输入/扫码获得的6位码
  if (!followerId || !bindCode) return { code: 400, message: '参数不完整' }

  const now = Date.now()
  
  // 查找未过期的验证码
  const res = await db.collection('bind_codes').where({
    code: bindCode,
    expiresAt: db.command.gt(now)
  }).getOne()

  if (!res.data) {
    return { code: 404, message: '绑定码无效或已过期，请刷新后再试' }
  }

  const targetUserId = res.data.userId // 被监控人 ID

  if (targetUserId === followerId) {
    return { code: 400, message: '不能绑定自己' }
  }

  // 建立绑定：将 targetUserId 的紧急联系人设置为 followerId
  await db.collection('users').doc(targetUserId).update({
    contactId: followerId
  })

  // 消费后立即销毁，防止重复撞码
  await db.collection('bind_codes').doc(res.data._id).remove()

  return { code: 0, message: '成功绑定关注对象！' }
}

函数 3：heartbeat（设备上报心跳）
import cloud from '@lafjs/cloud'

const db = cloud.database()

export default async function (ctx: FunctionContext) {
  const { userId, deviceToken } = ctx.body
  if (!userId) return { code: 400, message: '缺少 userId' }

  const now = Date.now()
  const user = await db.collection('users').doc(userId).get()

  if (user.data) {
    await db.collection('users').doc(userId).update({
      lastActiveTime: now,
      ...(deviceToken && { deviceToken })
    })
  } else {
    // 新用户首次上报
    await db.collection('users').add({
      _id: userId,
      deviceToken: deviceToken || '',
      lastActiveTime: now,
      thresholdMinutes: 120, // 默认 2 小时无活动告警
      contactId: null,
      isAlerted: false
    })
  }

  return { code: 0, message: 'pong' }
}

函数 4：watchdog（看门狗：配置定时触发器，如每 5 分钟跑一次）
import cloud from '@lafjs/cloud'
import apn from 'apn'

const db = cloud.database()

// 初始化 APNs 配置
const apnProvider = new apn.Provider({
  token: {
    key: Buffer.from(process.env.APNS_P8_KEY || '', 'utf-8'),
    keyId: process.env.APNS_KEY_ID || '',
    teamId: process.env.APNS_TEAM_ID || '',
  },
  production: true // 开发环境传 false，App Store 正式包传 true
})

export default async function (ctx: FunctionContext) {
  const now = Date.now()
  const users = await db.collection('users').get()

  for (const user of users.data) {
    // 无最后活动时间或无绑定联系人则跳过
    if (!user.lastActiveTime || !user.contactId) continue

    const thresholdMs = (user.thresholdMinutes || 120) * 60 * 1000
    const diff = now - user.lastActiveTime

    // 判断超时且尚未发送警报
    if (diff > thresholdMs && !user.isAlerted) {
      // 找到关联的紧急联系人
      const contact = await db.collection('users').doc(user.contactId).get()
      
      if (contact.data && contact.data.deviceToken) {
        const note = new apn.Notification()
        note.expiry = Math.floor(Date.now() / 1000) + 3600 // 1小时有效
        note.badge = 1
        note.sound = "default"
        note.title = "⚠️ 警报：“还在”安全监控"
        note.body = `您关注的对象超过 ${user.thresholdMinutes || 120} 分钟未有手机活动，请及时联系确认安全！`
        note.topic = process.env.APP_BUNDLE_ID

        // 发送 APNs 推送给关注人
        await apnProvider.send(note, contact.data.deviceToken)
        
        // 标记已告警，防止重复骚扰
        await db.collection('users').doc(user._id).update({ isAlerted: true })
      }
    } else if (diff <= thresholdMs && user.isAlerted) {
      // 重新恢复活动，解除告警状态
      await db.collection('users').doc(user._id).update({ isAlerted: false })
    }
  }

  return 'Watchdog run successfully'
}

在 Laf 的云函数设置中，为 watchdog 添加 Cron 触发器：0 */5 * * * *（每 5 分钟自动运行一次）。
第三部分：iOS App 端核心代码 (Swift / SwiftUI)
1. 网络请求与数据模型 (APIService.swift)
import Foundation

class APIService {
    static let shared = APIService()
    // 替换为你的 Laf 应用 AppURL
    private let baseURL = "https://<YOUR-LAF-APP-ID>.laf.run"
    
    // 1. 发送心跳
    func sendHeartbeat(userId: String, deviceToken: String?, completion: ((Bool) -> Void)? = nil) {
        guard let url = URL(string: "\(baseURL)/heartbeat") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = ["userId": userId, "deviceToken": deviceToken ?? ""]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request) { data, _, error in
            completion?(error == nil)
        }.resume()
    }
    
    // 2. 获取绑定码
    func fetchBindCode(userId: String, completion: @escaping (String?, String?) -> Void) {
        guard let url = URL(string: "\(baseURL)/generate-bind-code") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = ["userId": userId]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request) { data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let resData = json["data"] as? [String: Any] else {
                completion(nil, nil)
                return
            }
            completion(resData["bindCode"] as? String, resData["qrContent"] as? String)
        }.resume()
    }
    
    // 3. 执行绑定
    func bindUser(followerId: String, bindCode: String, completion: @escaping (Bool, String) -> Void) {
        guard let url = URL(string: "\(baseURL)/bind-user") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = ["followerId": followerId, "bindCode": bindCode]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request) { data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                completion(false, "网络错误")
                return
            }
            let success = (json["code"] as? Int) == 0
            let msg = (json["message"] as? String) ?? ""
            completion(success, msg)
        }.resume()
    }
}

2. 本地传感器与心跳调度 (MotionTracker.swift)
import Foundation
import CoreMotion

class MotionTracker {
    static let shared = MotionTracker()
    private let pedometer = CMPedometer()
    private var lastStepCount: Int = 0
    
    // 定时检查传感器并向 Laf 发送心跳
    func startMonitoring(userId: String, deviceToken: String?) {
        Timer.scheduledTimer(withTimeInterval: 15 * 60, repeats: true) { _ in
            let now = Date()
            let fifteenMinsAgo = now.addingTimeInterval(-15 * 60)
            
            if CMPedometer.isStepCountingAvailable() {
                self.pedometer.queryPedometerData(from: fifteenMinsAgo, to: now) { data, _ in
                    let steps = data?.numberOfSteps.intValue ?? 0
                    // 如果有步数产生，触发心跳
                    if steps > 0 {
                        APIService.shared.sendHeartbeat(userId: userId, deviceToken: deviceToken)
                    }
                }
            } else {
                // 不支持步数计数的设备，定时做基础保活心跳
                APIService.shared.sendHeartbeat(userId: userId, deviceToken: deviceToken)
            }
        }
    }
}

3. iOS 接入 APNs (AppDelegate.swift)
import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    
    var deviceTokenString: String?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        // 注册远程推送
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
        return true
    }

    // 获取到 Device Token
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        self.deviceTokenString = token
        print("APNs Device Token: \(token)")
    }
}

4. 二维码生成与绑定 UI (BindingView.swift)
利用 SwiftUI 原生 CoreImage 快速生成二维码，并支持手输 6 位数字码。
import SwiftUI
import CoreImage.CIFilterBuiltins

struct BindingView: View {
    let currentUserId: String
    
    @State private var bindCode: String = "------"
    @State private var qrImage: UIImage?
    @State private var inputCode: String = ""
    @State private var alertMessage: String = ""
    @State private var showAlert = false

    let context = CIContext()
    let filter = CIFilter.qrCodeGenerator()

    var body: some View {
        VStack(spacing: 25) {
            Text("被关注：展示二维码或数字码")
                .font(.headline)
            
            if let qrImage = qrImage {
                Image(uiImage: qrImage)
                    .resizable()
                    .interpolation(.none)
                    .frame(width: 180, height: 180)
            }
            
            Text("5分钟有效数字码: \(bindCode)")
                .font(.title2)
                .bold()
                .monospacedDigit()
            
            Button("刷新绑定码") {
                loadBindCode()
            }
            
            Divider().padding(.vertical)
            
            Text("去关注：输入对方的 6 位数字码")
                .font(.headline)
            
            TextField("输入 6 位数字", text: $inputCode)
                .keyboardType(.numberPad)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .multilineTextAlignment(.center)
                .frame(width: 200)
            
            Button("确认绑定") {
                APIService.shared.bindUser(followerId: currentUserId, bindCode: inputCode) { success, msg in
                    DispatchQueue.main.async {
                        self.alertMessage = msg
                        self.showAlert = true
                    }
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .onAppear { loadBindCode() }
        .alert(alertMessage, isPresented: $showAlert) {
            Button("确定", role: .cancel) { }
        }
    }

    private func loadBindCode() {
        APIService.shared.fetchBindCode(userId: currentUserId) { code, qrContent in
            DispatchQueue.main.async {
                if let code = code, let qrContent = qrContent {
                    self.bindCode = code
                    self.qrImage = generateQRCode(from: qrContent)
                }
            }
        }
    }

    private func generateQRCode(from string: String) -> UIImage? {
        filter.message = Data(string.utf8)
        if let outputImage = filter.outputImage,
           let cgImage = context.createCGImage(outputImage, from: outputImage.extent) {
            return UIImage(cgImage: cgImage)
        }
        return nil
    }
}

第四部分：防撞码与运维说明
1. 碰撞率控制： • 6 位纯数字池容量为 90 万。如果系统活跃的“等待绑定请求”维持在 100 个以内，碰撞概率低至 0.01%。 • Laf 云函数中的 bind_codes 集合对 expiresAt 进行了精确定时过滤，且绑定成功即刻销毁（remove），即使偶发碰撞也几乎无影响。
2. 离线处理逻辑： • 如果被关注人的手机因关机、没电、掉水里导致 App 停止发送心跳，云端 watchdog 会在超时时间到了之后照常触发警报推送给关注人，确保了安全兜底逻辑的闭环。