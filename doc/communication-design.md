# StillHere 移动端与服务端通信设计

## 1. 整体架构

```
┌─────────────────┐         HTTPS/JSON         ┌───────────────────────┐
│   iOS App        │ ────────────────────────── │  Cloudflare Workers   │
│   (SwiftUI)      │                             │  (api.padap.cn)        │
└─────────────────┘                             └───────────┬───────────┘
                                                             │
┌─────────────────┐         HTTPS/JSON         ┌───────────▼───────────┐
│  Android App     │ ────────────────────────── │  Cloudflare D1        │
│  (Jetpack Compose)│                            │  (SQLite 数据库)       │
└─────────────────┘                             └───────────┬───────────┘
                                                             │
                                               ┌─────────────▼─────────┐
                                               │  Apple APNs            │
                                               │  (仅 iOS 推送)          │
                                               └───────────────────────┘
```

- **API 端点**: `https://api.padap.cn`
- **通信协议**: HTTPS + JSON
- **超时**: 10 秒（连接 & 读取）
- **CORS**: 允许所有来源

---

## 2. 身份标识体系

### 2.1 Device ID (`userId`)

- 客户端首次启动时生成 UUID，持久化在本地（iOS: `UserDefaults`, Android: `SharedPreferences`）
- 作为全局惟一的用户标识（`userId`），在所有 API 请求中传递
- 服务端 `users` 表以此为主键

### 2.2 关心码 (`careCode`)

- 通过 `SHA-256(deviceId)` 单向哈希派生：
  - 取哈希 hex 字符串的第 8–13 位（0-indexed，即第 9–14 个字符），共 6 位大写字母
- **不可反推**：无法从关心码还原出 deviceId
- 用于添加关心关系、问安、查询状态等场景

```
deviceId  →  SHA-256  →  hex (64 chars)  →  hex[8..13]  →  6 位大写关心码
```

### 2.3 关心码生成逻辑对齐

三端（iOS / Android / Server）使用完全相同的切片逻辑：`hex.substring(8, 14).toUpperCase()`

---

## 3. API 路由一览

| 路径 | 方法 | 用途 | 服务端文件 |
|------|------|------|------------|
| `/heartbeat` | POST | 心跳上报 | `routes/heartbeat.ts` |
| `/care` | POST | 创建关心关系 | `routes/care.ts` |
| `/care` | DELETE | 删除关心关系 | `routes/care.ts` |
| `/cared-status` | POST | 批量查询关心对象活动状态 | `routes/cared-status.ts` |
| `/cared-by-me` | GET | 查询关注自己的人 | `routes/cared-by-me.ts` |
| `/alert` | POST | 上报空闲告警 | `routes/alert.ts` |
| `/alert/cancel` | POST | 取消告警（恢复活动） | `routes/alert.ts` |
| `/pending-alerts` | GET | 拉取待处理告警（Android 替代推送） | `routes/pending-alerts.ts` |
| `/greeting` | POST | 发送问安 | `routes/greeting.ts` |
| `/greeting/reply` | POST | 回复问安 | `routes/greeting.ts` |
| `/pending-greetings` | GET | 拉取未回复的问安 | `routes/greeting.ts` |
| `/dashboard` | GET | 管理后台（需 Google OAuth） | `routes/dashboard.ts` |
| `/` | GET | 公众介绍页 | `index.ts` |
| `/auth/login` | GET | Google OAuth 登录跳转 | `lib/auth.ts` |
| `/auth/callback` | GET | OAuth 回调处理 | `lib/auth.ts` |
| `/auth/logout` | GET | 登出 | `lib/auth.ts` |

---

## 4. 详细 API 设计

### 4.1 心跳上报 `POST /heartbeat`

**客户端触发时机**：前台每 3 秒轮询一次

**请求体**：
```json
{
  "userId": "device-uuid",
  "careCode": "A1B2C3",
  "isCharging": false
}
```

**服务端处理**：
1. `upsert` users 表：更新 `last_active_time`、`is_charging`、`last_city`（通过 Cloudflare IP 地理定位），标记 `online_status = 'online'`
2. 如果用户之前是离线状态（`online_status = 'offline'`），插入一条 `alert_type = 'online'` 的恢复告警
3. 通过 `careCode` 精确匹配 `care_relations.to_code`，返回被关心人数 `caredByCount`

**返回**：
```json
{
  "ok": true,
  "timestamp": 1722000000,
  "caredByCount": 3
}
```

---

### 4.2 关心关系管理 `POST /care` & `DELETE /care`

#### 创建关心关系 `POST /care`

```json
// 请求
{
  "fromUserId": "device-uuid",
  "toCode": "A1B2C3"
}

// 返回
{ "ok": true, "existed": false }
```

- 关心码标准化：大写、取前 6 位
- 去重检查：同一对 `(from_device_id, to_code)` 不重复插入
- **隐私保护**：不存储昵称，昵称仅保留在客户端本地

#### 删除关心关系 `DELETE /care?fromUserId=xxx&toCode=YYY`

```json
{ "ok": true }
```

---

### 4.3 关心对象状态查询 `POST /cared-status`

**用途**：客户端刷新所关心的人的最近活动状态

**请求体**：
```json
{
  "codes": ["A1B2C3", "D4E5F6"]
}
```

**返回**：
```json
{
  "codes": {
    "A1B2C3": { "lastActive": 1722000000, "isCharging": false, "city": "Beijing, CN" },
    "D4E5F6": { "lastActive": null, "isCharging": false, "city": null }
  }
}
```

- `lastActive` 为空表示该关心码对应的用户尚未上报过心跳

---

### 4.4 关注我的人 `GET /cared-by-me?careCode=XXXXXX`

**用途**：查看谁在关心我，展示对方的关心码列表

**逻辑**：
1. 从 `care_relations` 查询 `to_code = 目标码` 的所有 `from_device_id`
2. 为每个 deviceId 计算关心码返回

**返回**：
```json
{
  "carers": [
    { "careCode": "A1B2C3" },
    { "careCode": "D4E5F6" }
  ]
}
```

- 客户端自行判断互相关心关系（比对本地 caring 列表与返回的 careCode）

---

### 4.5 告警系统

#### 告警上报 `POST /alert`

**触发时机**：客户端检测到空闲超过阈值 → 5 分钟倒计时 → 到时间后调用此接口

```json
{
  "userId": "device-uuid",
  "careCode": "A1B2C3",
  "idleMinutes": 30,
  "isCharging": false
}
```

**服务端处理**：
1. 插入 `alerts` 表（`alert_type = 'idle'`）
2. 查询所有关心此人的人（`care_relations.to_code`），向有 APNs device_token 的 iOS 设备推送通知
3. 返回关心人数和推送成功数

#### 取消告警 `POST /alert/cancel`

```json
{
  "userId": "device-uuid",
  "careCode": "A1B2C3"
}
```

- 将所有未解决的告警标记为 `is_resolved = 1`
- 推送恢复通知给所有关心人

#### 待处理告警拉取 `GET /pending-alerts?deviceId=xxx`

**用途**：Android 无 FCM 推送通道，通过轮询拉取告警

- 查询当前用户关心的所有人的告警
- 返回最近 50 条，按时间倒序

---

### 4.6 问安系统

#### 发送问安 `POST /greeting`

```json
{
  "fromUserId": "device-uuid",
  "toCode": "A1B2C3",
  "message": "最近还好吗？"
}
```

**服务端处理**：
1. 插入 `greetings` 表（`notified = 0`）
2. 异步推送 APNs 通知给接收方（如果有 iOS device_token）

#### 回复问安 `POST /greeting/reply`

```json
{
  "greetingId": 42,
  "reply": "一切安好！",
  "fromUserId": "device-uuid-of-replier"
}
```

**服务端处理**：
1. 更新原问候的 `reply` 字段
2. 创建反向问候记录（`from_device_id = 回复者`, `to_code = 原发送者的关心码`），确保原发送方能在 `pending-greetings` 中拉取到回复
3. `reply_to_id` 防止无限循环反向记录

#### 拉取未回复问安 `GET /pending-greetings?careCode=XXXXXX`

- 查询 `to_code = 目标码` 且 `reply IS NULL` 且 `notified = 0` 的问安
- 返回同时标记 `notified = 1`（**去重机制**：已通知的记录不再返回）

**返回**：
```json
{
  "greetings": [
    {
      "id": 42,
      "fromCareCode": "D4E5F6",
      "message": "最近还好吗？",
      "createdAt": 1722000000
    }
  ]
}
```

---

## 5. 数据库表结构

### 5.1 `users` — 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | TEXT PK | 客户端 UUID（deviceId） |
| `device_token` | TEXT | APNs device token |
| `last_active_time` | INTEGER | 最后活动时间戳（秒） |
| `is_charging` | INTEGER | 是否充电 (0/1) |
| `last_city` | TEXT | IP 归属城市 |
| `online_status` | TEXT | online / offline |
| `created_at` | INTEGER | 首次心跳时间 |

### 5.2 `care_relations` — 关心关系表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增 |
| `from_device_id` | TEXT | 发起关心的设备 ID |
| `to_code` | TEXT | 被关心方的关心码（6位） |
| `created_at` | INTEGER | 创建时间 |

### 5.3 `alerts` — 告警事件表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增 |
| `user_id` | TEXT | 被关心者 deviceId |
| `care_code` | TEXT | 被关心者关心码 |
| `alert_type` | TEXT | idle / offline / online |
| `idle_minutes` | INTEGER | 空闲分钟数 |
| `is_charging` | INTEGER | 是否充电 |
| `is_resolved` | INTEGER | 是否已恢复 (0/1) |
| `created_at` | INTEGER | 告警时间 |
| `resolved_at` | INTEGER | 恢复时间 |

### 5.4 `greetings` — 问安消息表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增 |
| `from_device_id` | TEXT | 发送者 deviceId |
| `to_code` | TEXT | 接收方关心码 |
| `message` | TEXT | 问安内容 |
| `reply` | TEXT | 回复内容 |
| `replied_at` | INTEGER | 回复时间 |
| `notified` | INTEGER | 是否已通知客户端 (0/1) |
| `reply_to_id` | INTEGER | 反向记录指向原问候 id |
| `created_at` | INTEGER | 发送时间 |

---

## 6. 客户端轮询机制

### 6.1 iOS（SwiftUI）

`ContentView` 使用 `scenePhase` 感知前后台状态：

| 场景 | 轮询间隔 | 操作 |
|------|---------|------|
| 前台 (`active`) | **3 秒** | 心跳上报 + 问安拉取 + 关心人数刷新 |
| 后台 (`background`) | **60 秒** | 同上（若系统允许） |

```swift
// 前台快速轮询，后台慢速
let interval: UInt64 = scenePhase == .active ? 3_000_000_000 : 60_000_000_000
try? await Task.sleep(nanoseconds: interval)
```

**两个独立 `.task` 协程**：
- 协程 1：心跳刷新 → `sendHeartbeatNow()`，每 3/60 秒
- 协程 2：问安拉取 → `fetchPendingGreetings()`，每 3/60 秒

### 6.2 Android（Jetpack Compose）

Android 当前为固定 3 秒轮询（未区分前后台）：

```kotlin
// 心跳
LaunchedEffect(Unit) {
    delay(30_000)        // 首次等待 30 秒
    while (true) {
        Reporter.report()
        delay(3_000)     // 每 3 秒
    }
}

// 问安
LaunchedEffect(Unit) {
    delay(5000)          // 首次等待 5 秒
    while (true) {
        Reporter.fetchPendingGreetings(...)
        delay(3_000)     // 每 3 秒
    }
}
```

---

## 7. 通知机制

### 7.1 iOS — APNs 远程推送

服务端通过 **Cloudflare Workers** 直接调用 Apple APNs HTTP/2 API 发送远程推送：

- 使用 ES256 JWT 签名（`lib/apns.ts`）
- 触发场景：
  - 告警上报时 → 推送给所有关心人
  - 告警取消/恢复时 → 推送给所有关心人
  - 问安发送时 → 推送给接收方
  - 回复时 → 推送给原发送方

### 7.2 iOS — 本地通知

在无法及时依赖 APNs 时，客户端直接发送本地通知：

- **告警通知**：`"已 X 分钟无活动，5分钟内未取消将通知关心人"`
- **问安通知**：`"收到一条新的问安"`

### 7.3 Android — 本地通知

Android 无 FCM 推送通道，完全依赖本地通知：

- **告警通知**：通过 `NotificationCompat` → `StillHereApp.GREETING_CHANNEL_ID`
- **问安通知**：同上频道
- **待处理告警**：通过轮询 `GET /pending-alerts` 替代推送

---

## 8. 告警延迟上报机制（5 分钟取消窗口）

```
┌──────────────┐    检测到空闲超时    ┌──────────────┐
│  MonitorManager │ ────────────────── │  显示 UI Banner  │
│  (本地)         │                    │  + 系统通知      │
└──────┬───────┘                    └──────┬───────┘
       │                                   │
       │  启动 5 分钟倒计时                  │ 用户点击「取消」
       │  pendingAlertTimer                 │ cancelPendingAlert()
       │                                   │
       ▼                                   ▼
  ┌─────────┐                        ┌──────────┐
  │ 等待 5 分钟 │                      │ 清除计时器  │
  └────┬────┘                        │ 不上报服务器 │
       │                             └──────────┘
       │ 超时未取消
       ▼
  ┌──────────────┐
  │ firePendingAlert() │
  │ POST /alert       │────────── 服务端广播给关心人 + APNs 推送
  └──────────────┘
```

**关键数据流**：
1. `MonitorManager.checkLocalAlert()` 检测到空闲 → 发送本地通知
2. 设置 `pendingAlertMinutes`（UI 显示倒计时），启动 5 分钟 `Timer`
3. 5 分钟内用户可点击 Banner 的「取消」按钮 → `cancelPendingAlert()` → 清除计时器，移除通知
4. 5 分钟后未取消 → `firePendingAlert()` → `Reporter.reportAlert()` → `POST /alert`
5. 服务端收到/alert → 插入告警记录 → 推送给所有关心人

---

## 9. 问安消息去重机制

### 问题
客户端每次轮询 `/pending-greetings` 都会返回所有未回复的问安，导致重复显示。

### 解决方案
- `greetings` 表新增 `notified` 字段（默认 0）
- 服务端首次返回问安时，立即 `UPDATE greetings SET notified = 1` 标记为"已通知"
- 后续轮询仅查询 `notified = 0` 的记录

### 问安回复可见性
- 回复方调用 `POST /greeting/reply` 时附带 `fromUserId`
- 服务端创建**反向问候记录**：将回复作为一条新 `greeting`，to_code 指向原发送者的关心码
- `reply_to_id` 指向原问候 id，防止无限链式反向

这样原发送方在调用 `GET /pending-greetings` 时就能看到回复消息。

---

## 10. 离线判定与 Cron 定时任务

### Watchdog 定时任务

Cloudflare Workers Cron Trigger，每 5 分钟执行一次：

```
职责：检测超过 24 小时未心跳的用户 → 标记为 'offline' → 推送通知给关心人
```

- 查询条件：`online_status = 'online'` 且 `last_active_time < 24小时前`
- 标记 `online_status = 'offline'`
- 通过 APNs 推送给关心人

---

## 11. 服务端部署架构

| 组件 | 技术 | 说明 |
|------|------|------|
| 计算 | Cloudflare Workers | 边缘计算，全球低延迟 |
| 数据库 | Cloudflare D1 | SQLite 兼容的分布式数据库 |
| 推送 | Apple APNs HTTP/2 | JWT 签名，无需外部依赖 |
| 认证 | Google OAuth 2.0 | Dashboard 管理后台使用 |
| 定时 | Workers Cron Trigger | 每 5 分钟执行 Watchdog |
| 域名 | `api.padap.cn` | 自定义域名绑定 Worker |
| 兼容性日期 | `2024-09-23` | Workers 运行时兼容性 |

---

## 12. 客户端 Reporer 封装

双端 API 调用层均封装为单例对象：

| 平台 | 文件 | HTTP 库 |
|------|------|--------|
| iOS | `Reporter.swift` (actor) | `URLSession` |
| Android | `Reporter.kt` (object) | `OkHttp` |

### 方法对照表

| 方法 | iOS | Android |
|------|-----|---------|
| 心跳上报 | `report(source:event:appState:isCharging:)` | `report(isCharging:)` |
| 创建关心 | `registerCare(toCode:)` | `registerCare(toCode:)` |
| 删除关心 | `unregisterCare(toCode:)` | `unregisterCare(toCode:)` |
| 查询状态 | `fetchCaredStatus(codes:)` | `fetchCaredStatus(codes:)` |
| 关注我的人 | `fetchCaredByMe(careCode:)` | `fetchCaredByMe(careCode:)` |
| 上报告警 | `reportAlert(idleMinutes:isCharging:)` | `reportAlert(idleMinutes:isCharging:)` |
| 取消告警 | `cancelAlert()` | `cancelAlert()` |
| 待处理告警 | —（使用 APNs） | `fetchPendingAlerts()` |
| 发送问安 | `sendGreeting(toCode:message:)` | `sendGreeting(toCode:message:)` |
| 回复问安 | `replyGreeting(greetingId:reply:)` | `replyGreeting(greetingId:reply:)` |
| 拉取问安 | `fetchPendingGreetings(careCode:)` | `fetchPendingGreetings(careCode:)` |

---

## 13. 隐私与安全设计

1. **单向哈希**：关心码通过 SHA-256 派生，无法从关心码反推出 deviceId
2. **昵称本地存储**：服务端 `care_relations` 不存储昵称，昵称仅保留在客户端 `CareStore`
3. **无用户注册**：无需手机号或邮箱注册，纯设备 UUID 标识
4. **IP 归属城市**：通过 Cloudflare 边缘节点获取（`request.cf.city`），不存储精确 IP
