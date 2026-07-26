# activity-reporting Specification

## Purpose
TBD - created by archiving change migrate-to-cloudflare-workers. Update Purpose after archive.
## Requirements
### Requirement: 客户端唯一标识生成

被监控人 App SHALL 在首次启动时生成全局唯一 `userId`（UUID），并持久化存储在 iOS Keychain（跨 App 重装保留）。后续所有上报请求 MUST 携带该 `userId`。

#### Scenario: 首次启动生成 userId

- **WHEN** App 首次启动且 Keychain 中无已存 `userId`
- **THEN** 系统生成新 UUID，写入 Keychain，并用于后续所有心跳上报

#### Scenario: 重装后保留 userId

- **WHEN** 用户卸载并重装 App
- **THEN** 系统从 Keychain 恢复既有 `userId`，不生成新 ID（保持绑定关系）

### Requirement: 事件驱动活动上报

被监控人 App SHALL 在以下系统活动事件发生时，调用后端 `POST /heartbeat` 上报"我还活着"信号，而非依赖定时器轮询。上报 MUST 携带 `userId` 与 `deviceToken`，SHOULD 携带当前充电状态（`isCharging`）。

事件源（三层防御，互为补充）：

- **L1 主信号**：SLC 显著位置变化（`CLLocationManager.startMonitoringSignificantLocationChanges`）触发；CoreMotion Activity 状态变化（`CMMotionActivityManager.startActivityUpdates`，配 `UIBackgroundModes: motion`）触发
- **L2 兜底**：`BGAppRefreshTask` 系统调度触发；充电状态变化（`UIDevice.batteryStateDidChangeNotification`）触发
- **L3 主动探测（V2，MVP 不实现）**：Silent Push 服务器主动唤醒

#### Scenario: SLC 显著位置变化触发上报

- **WHEN** 被监控人手机发生基站切换或 500m+ 位置显著变化
- **THEN** 系统唤醒 App（即使已被杀），App 调用 `POST /heartbeat` 上报当前时间与充电状态

#### Scenario: CoreMotion 活动状态变化触发上报

- **WHEN** CoreMotion 检测到被监控人活动状态从静止转为走路/跑步/驾车/骑行
- **THEN** App 调用 `POST /heartbeat` 上报

**注**：CoreMotion 后台回调可靠性需经前置 spike 验证。若 spike 不通过，此场景降级为仅前台生效，L1 退化为 SLC 单信号。

#### Scenario: BGAppRefreshTask 兜底触发

- **WHEN** 系统调度 `BGAppRefreshTask`（时机不可控）
- **THEN** App 调用 `POST /heartbeat` 上报，并重新提交下一次 BGAppRefresh 请求

#### Scenario: 充电状态变化触发

- **WHEN** 被监控人手机插上电源或拔下电源
- **THEN** App（若在后台运行中）调用 `POST /heartbeat` 上报，含 `isCharging` 字段

#### Scenario: App 前台触发

- **WHEN** 被监控人将 App 切到前台
- **THEN** App 调用 `POST /heartbeat` 上报

### Requirement: 心跳端点接收与更新

后端 `POST /heartbeat` 端点 SHALL 接收 `{ userId, deviceToken?, isCharging? }`，在 D1 `users` 表中 upsert 用户记录，更新 `last_active_time` 为当前时间戳。若 `deviceToken` 非空，SHALL 一并更新；若 `isCharging` 非空，SHALL 更新 `is_charging` 字段。

#### Scenario: 已有用户心跳更新

- **WHEN** 收到 `POST /heartbeat` 且 `userId` 在 `users` 表已存在
- **THEN** 系统更新该用户 `last_active_time` 为当前时间，并按需更新 `device_token` 与 `is_charging`，返回 `{ code: 0, message: "pong" }`

#### Scenario: 新用户首次心跳创建

- **WHEN** 收到 `POST /heartbeat` 且 `userId` 在 `users` 表不存在
- **THEN** 系统创建新记录，写入 `id`、`device_token`、`last_active_time`、`is_charging`、`created_at`，默认 `threshold_minutes=120`、`contact_id=null`、`is_alerted=0`，返回 `{ code: 0, message: "pong" }`

#### Scenario: 缺少 userId 拒绝

- **WHEN** 收到 `POST /heartbeat` 但 `userId` 为空
- **THEN** 系统返回 `{ code: 400, message: "缺少 userId" }`

### Requirement: SLC 定位权限引导

App SHALL 在首次启动时请求"始终使用定位"权限（`CLLocationManager.requestAlwaysAuthorization`），用于 SLC 后台唤醒。若用户拒绝，App SHALL 明示告警"安全监控功能将不可用"，且不进入监测状态。

#### Scenario: 用户授予始终定位权限

- **WHEN** 用户首次启动 App 并授予"始终使用定位"权限
- **THEN** 系统启动 SLC 监测，进入正常工作状态

#### Scenario: 用户拒绝定位权限

- **WHEN** 用户拒绝"始终使用定位"权限
- **THEN** 系统显示告警说明"独居安全监控需要始终定位权限以检测您的活动"，不启动 SLC，App 功能受限状态明示

