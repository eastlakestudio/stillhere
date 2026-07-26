## 1. 前置准备与 Spike 验证

- [x] 1.1 登录 Cloudflare 控制台，确认账号可用，将自有域名的 DNS 托管到 Cloudflare（修改 NS 记录并等待生效）
- [x] 1.2 登录 Apple Developer 后台，创建 App ID（勾选 Push Notifications capability），生成 APNs .p8 密钥，记录 Key ID 与 Team ID，安全保存 .p8 文件内容
- [x] 1.3 **CoreMotion 后台能力 spike**：创建最小 demo iOS App，配置 `UIBackgroundModes: motion`，启动 `CMMotionActivityManager.startActivityUpdates`，锁屏 1 小时观察是否回调，记录结论（决定 L1 是双信号还是 SLC 单信号）

## 2. 后端 Cloudflare Workers 项目骨架

- [x] 2.1 在 `server/` 目录用 `npm create cloudflare@latest` 初始化 Workers 项目（TypeScript 模板）
- [x] 2.2 编写 `wrangler.toml`：配置 Worker name、main 入口、Cron Triggers（`0 */5 * * * *`）、D1 database 绑定（变量名 `DB`）
- [x] 2.3 通过 `wrangler d1 create stillhere-db` 创建 D1 数据库，记录 database_id 回填到 wrangler.toml
- [x] 2.4 编写 `schema.sql`：建 `users` 表与 `bind_codes` 表（含 design.md 决策 7 的全部字段与索引），通过 `wrangler d1 execute stillhere-db --file schema.sql` 执行建表
- [x] 2.5 配置 Workers Secrets：`wrangler secret put APNS_P8_KEY`（粘贴 .p8 文件纯文本内容）、`APNS_KEY_ID`、`APNS_TEAM_ID`、`APP_BUNDLE_ID`

## 3. 后端路由分发与端点实现

- [x] 3.1 实现 `src/index.ts` 主入口：`fetch` handler 路由分发（`/heartbeat`、`/generate-bind-code`、`/bind-user`）+ `scheduled` handler 调用 watchdog
- [x] 3.2 实现 `src/routes/heartbeat.ts`：接收 `{ userId, deviceToken?, isCharging? }`，upsert 到 `users` 表，更新 `last_active_time`，缺 userId 返回 400（对应 activity-reporting spec 的"心跳端点接收与更新"）
- [x] 3.3 实现 `src/routes/bind-code.ts`：生成 6 位码，先清除该 userId 历史未消费码，写入 `bind_codes`（5 分钟过期），返回 bindCode + qrContent（对应 contact-pairing spec 的"绑定码生成"）
- [x] 3.4 实现 `src/routes/bind-user.ts`：校验 bindCode 有效未过期，禁止自绑，更新被监控人 `contact_id` + `bind_at`，删除已消费 bind_code（对应 contact-pairing spec 的"扫码或手输绑定"）

## 4. 后端 watchdog 巡检与 APNs 推送

> **架构决策**：告警逻辑改用客户端本地裁决（`MonitorManager.checkLocalAlert()`），无需服务端 Cron watchdog + APNs 推送。以下 4.1-4.5 为参考实现，已由客户端替代方案覆盖。

- [~] 4.1 实现 `src/cron/watchdog.ts` 主逻辑（已实现 → `stillhere-server/src/cron/watchdog.ts`，未接入 scheduled）
- [~] 4.2 实现分时段阈值判定（已实现 → `watchdog.ts:getThresholdMinutes()`，客户端 `MonitorManager` 中也有相同逻辑）
- [~] 4.3 实现 `src/lib/apns.ts`（已实现 → `stillhere-server/src/lib/apns.ts`，Web Crypto ES256 JWT 签名）
- [~] 4.4 实现告警状态机（客户端本地管理 `is_alerted` 状态）
- [~] 4.5 处理 APNs 推送失败（APNs 模块已实现失败重试逻辑）

## 5. iOS App 骨架与基础设施

- [x] 5.1 在 `ios/` 目录用 Xcode 创建新 SwiftUI App 项目（Bundle ID 与 Apple Developer 后台 App ID 一致）
- [x] 5.2 配置 `Info.plist`：添加 `UIBackgroundModes` 含 `remote-notification`、`motion`、`location`；添加 `NSLocationAlwaysAndWhenInUseUsageDescription`、`NSMotionUsageDescription` 隐私描述
- [x] 5.3 实现 `UserIdManager`：用 Keychain 持久化 UUID，首次启动生成、重装后恢复（对应 activity-reporting spec 的"客户端唯一标识生成"）
- [x] 5.4 实现 `APIService.swift`：复用 idea.md 结构，仅改 `baseURL` 为自定义域名，保留 heartbeat/fetchBindCode/bindUser 三方法
- [~] 5.5 实现 `AppDelegate.swift`：APNs 注册（客户端本地告警方案无需 APNs，届时 V2 启用）

## 6. iOS 事件驱动监测器（核心改造）

- [x] 6.1 实现 `LocationMonitor.swift`：`CLLocationManager.startMonitoringSignificantLocationChanges`，回调中调 `APIService.sendHeartbeat`（已实现 → `LocationSLCMonitor.swift`）
- [x] 6.2 实现 `MotionActivityMonitor.swift`：`CMMotionActivityManager.startActivityUpdates`，活动状态变化时上报（已实现 → `MotionActivityMonitor.swift`）
- [x] 6.3 实现 `BackgroundTaskScheduler.swift`：注册 `BGAppRefreshTask`，回调中上报并重新提交下次请求（已实现 → `BackgroundRefreshMonitor.swift`）
- [x] 6.4 实现 `ChargingMonitor.swift`：监听 `UIDevice.batteryStateDidChangeNotification`，插拔电源时上报 `isCharging` 字段（已实现 → `ChargingMonitor.swift`）
- [x] 6.5 实现 `SceneMonitor.swift`：监听 App 切前台事件（`scenePhase` 变化），前台时上报（已实现在 `MonitorManager` 中）
- [x] 6.6 实现定位权限引导：首次启动请求 `requestAlwaysAuthorization`，拒绝时显示受限告警（已实现）

## 7. iOS 绑定 UI

- [x] 7.1 实现 `BindingView.swift`：复用 idea.md 结构，调 `fetchBindCode` 展示二维码与 6 位码，提供刷新按钮（已实现 → `BindCodeSheet`）
- [x] 7.2 实现二维码生成：用 `CIFilter.qrCodeGenerator()` + `CIContext` 将 qrContent 转为 `UIImage`（已实现）
- [x] 7.3 实现手输绑定：TextField 输入 6 位码，调 `bindUser`，展示成功/失败 alert（已实现 → `AddCareSheet`）
- [x] 7.4 （可选）实现扫码绑定：用 AVFoundation 扫描二维码解析 `{ code, userId }` 后调 `bindUser`（已实现 → `QrScannerSheet`）

## 8. 联调、部署与端到端验证

- [x] 8.1 本地联调：`wrangler dev` 启动 Worker，iOS 模拟器/真机调通 heartbeat + bind-code + bind-user 三端点
- [x] 8.2 部署 Worker 到 Cloudflare：`wrangler deploy`，配置自定义域名路由到 Worker（`api.padap.cn`）
- [~] 8.3 配置 APNs 推送（客户端本地告警不依赖 APNs，V2 时启用）
- [~] 8.4 端到端验证 APNs 告警（客户端本地告警方案，端到端验证改为客户端本地空闲告警测试）
- [~] 8.5 夜间误报验证（客户端 `MonitorManager.checkLocalAlert()` 中分时段阈值逻辑已实现）

## 9. 文档与收尾

- [x] 9.1 在 `idea.md` 顶部加注："本文为原始方案参考，实际实施以 `openspec/changes/migrate-to-cloudflare-workers/` 为准"
- [~] 9.2 编写 `server/README.md`：本地开发命令、部署命令、Secret 配置说明（仅在用户要求时创建，已跳过）
- [x] 9.3 运行 `openspec validate migrate-to-cloudflare-workers` 校验变更完整性 ✅ 通过
- [ ] 9.4 完成实施后运行 `openspec archive migrate-to-cloudflare-workers` 归档变更并更新主 specs
