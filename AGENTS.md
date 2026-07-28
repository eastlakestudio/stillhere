# StillHere 开发者指南

## 项目结构

3 个子项目，同一仓库：

| 目录 | 平台 | 语言/框架 |
|------|------|-----------|
| `ios/AnhaoSpike/` | iOS | Swift 6 + SwiftUI |
| `android/` | Android | Kotlin + Jetpack Compose (Material 3) |
| `stillhere-server/` | 后端 | TypeScript → Cloudflare Workers + D1 |

## 常用命令

```bash
# iOS 编译
xcodebuild -project ios/AnhaoSpike/AnhaoSpike.xcodeproj -scheme AnhaoSpike \
  -destination 'platform=iOS Simulator,name=iPhone 17' build

# Android 编译
cd android && ./gradlew assembleDebug

# 服务端本地调试
cd stillhere-server && npm run dev

# 服务端部署
cd stillhere-server && npm run deploy

# 数据库迁移
cd stillhere-server && npm run db:init       # 生产
cd stillhere-server && npm run db:local      # 本地 D1
```

## 编译环境

- iOS: Xcode 26+ (macOS, Swift 6.0, target iOS 17.0)
- Android: JDK + Android SDK (minSdk 26, targetSdk 34)，使用 Gradle Wrapper
- Server: Node.js + Wrangler CLI (`npx wrangler`)

## 身份体系（关键架构）

`deviceId` 是全局唯一用户标识，`careCode` 由此派生：

```
deviceId = UUID (或 Android 上由 ANDROID_ID 派生)
careCode = SHA-256(deviceId) → hex → [8:14) → uppercase (6位)
```

- iOS: deviceId 存在 **Keychain**（`com.eastlakestudio.stillhere`，卸载重装不丢失）
- Android: deviceId 由 `Settings.Secure.ANDROID_ID` 确定性派生（同签名下跨重装一致）
- 关心码**不可反推** deviceId，服务端只通过 careCode 做反向查询
- `toCareCode()` 三端实现一致：iOS `Reporter.swift:394`，Android `Reporter.kt:400`，Server `shared.ts:34`

## API 端点

Base URL: `https://api.padap.cn`（Cloudflare Worker 自定义域名）

| 路径 | 方法 | 用途 |
|------|------|------|
| `/heartbeat` | POST | 心跳上报 |
| `/care` | POST / DELETE | 创建/删除关心关系 |
| `/cared-status` | POST | 批量查询被关心者状态 |
| `/cared-by-me` | GET | 查询关注我的人 |
| `/alert` | POST | 上报空闲告警 |
| `/alert/cancel` | POST | 取消告警 |
| `/greeting` | POST | 发送问安 |
| `/greeting/reply` | POST | 回复问安 |
| `/pending-greetings` | GET | 拉取未回问安 |
| `/pending-alerts` | GET | Android 拉取待处理告警（替代推送） |

## 服务端架构

- Cloudflare Workers + D1 (SQLite)，每 5 分钟 Cron Watchdog 检测离线
- APNs 通过 Web Crypto + HTTP/2 直连 Apple，无三方依赖
- 管理后台 `/dashboard` 需 Google OAuth 登录

## iOS 特有细节

- `Reporter` 是 `actor`（Swift 并发单例），`MonitorManager` 需 `@MainActor`
- 使用 XcodeGen (`project.yml`) 生成 `.xcodeproj`
- 后台模式: `UIBackgroundModes` = location, motion, remote-notification
- BGTaskScheduler: `com.eastlakestudio.stillhere.refresh`

## Android 特有细节

- 告警用 `WorkManager` (`BackgroundWorker`) 兜底心跳 + 问安轮询
- 有本地通知（`NotificationCompat`），无线程外推送（无 FCM）
- `Reporter` 是 Kotlin `object`（单例），`init(context)` 必须在 `Application.onCreate()` 调用
