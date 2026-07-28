# 晴好 StillHere — 独居安全守护 App 需求文档

## 一、产品概述

### 1.1 产品愿景

**晴好**（原"安好"）是一款面向独居人群的安全监测 App。被关心者安装 App 后，系统自动感知其手机活动状态（步数、位置变化、充电、屏幕唤醒），定时上报心跳。长时间无活动时自动告警给家人，让关心不缺席。

### 1.2 目标用户

| 角色 | 描述 |
|------|------|
| 被关心者 | 独居人士，安装 App 接受活动监测，产生告警 |
| 关心者 | 被关心者的家人/朋友，接收告警通知，可查询状态、发送问安 |

### 1.3 核心价值

- **自动监测**：无需手动打卡，通过传感器自动感知日常活动
- **双向安心**：被关心者不需要主动报平安；关心者可随时查看状态
- **隐私优先**：无用户注册（纯设备 UUID），关心码不可反推身份，昵称只存本地

---

## 二、功能需求

### 2.1 设备身份

| 功能 | 描述 |
|------|------|
| 生成 Device ID | App 首次启动生成唯一设备 ID（iOS: Keychain UUID / Android: ANDROID_ID 派生）|
| 生成关心码 | SHA-256(deviceId) → hex[8:14) → 大写 6 位码，不可反推 |
| 关心码持久化 | 卸载重装后 Device ID / 关心码不变 |

### 2.2 关心关系管理

| 功能 | 描述 |
|------|------|
| 展示关心码 | 显示自己的 6 位关心码 + 二维码 |
| 扫码添加 | 扫描对方关心码二维码，建立关心关系 |
| 手动输入 | 手动输入 6 位关心码添加 |
| 关心列表 | 展示我关心的人列表，含昵称（本地存储）|
| 被关心列表 | 展示关心我的人的关心码列表 |
| 删除关心 | 解除关心关系 |
| 互关标识 | 标记双向关心关系 |

### 2.3 活动监测

| 功能 | 描述 |
|------|------|
| 运动感知 | iOS: SLC (Significant Location Change) + CoreMotion 计步器；Android: ActivityTransition + 加速度计 |
| 位置变化 | iOS: SLC；Android: 持续定位 |
| 充电状态 | 监测充电/断电事件 |
| 前台唤醒 | App 在前台时重置空闲计时器 |
| 守护时段 | 可配置多个守护时段（如 9:00-18:00），仅时段内才触发告警 |

### 2.4 心跳上报

| 功能 | 描述 |
|------|------|
| 前台心跳 | 每 3 秒 POST /heartbeat 到服务端 |
| 后台心跳 | iOS: 后台每 60 秒；Android: WorkManager 每 15 分钟 |
| 上报内容 | userId, careCode, isCharging |
| 返回内容 | 被关心人数 caredByCount |

### 2.5 告警系统

| 功能 | 描述 |
|------|------|
| 静置告警阈值 | 可配置 5-120 分钟（5 分钟档位），超过阈值无活动触发告警 |
| 充电忽略 | 可配置充电时是否忽略告警 |
| 告警倒计时 | 触发告警后显示 5 分钟取消窗口，超时上报服务端 |
| 本地通知 | 告警时发送系统通知（"已 X 分钟无活动，5分钟内未取消将通知关心人"）|
| 远程推送 | iOS: APNs 推送给关心者；Android: 无 FCM，轮询拉取 |
| 告警取消 | 被关心者恢复活动后自动取消告警 |
| 恢复通知 | 告警取消后通知关心者"已恢复活动" |

### 2.6 问安

| 功能 | 描述 |
|------|------|
| 发送问安 | 关心者向被关心者发送问安消息 |
| 接收问安 | 被关心者收到问安，显示回复界面 |
| 快捷回复 | "晴好"快捷回复 |
| 自定义回复 | 输入文本回复 |
| 系统通知 | 收到问安时本地通知 + APNs（iOS）推送 |

### 2.7 状态查询

| 功能 | 描述 |
|------|------|
| 关心对象状态 | 批量查询被关心者的最近活跃时间、是否充电、IP 归属城市 |
| 定时刷新 | 每 3 秒刷新被关心者状态 |

---

## 三、平台与终端

| 终端 | 平台 | 技术栈 |
|------|------|--------|
| iOS App | iOS 17.0+ | Swift 6 + SwiftUI |
| Android App | Android 8.0+ (API 26) | Kotlin + Jetpack Compose (Material 3) |
| 服务端 | Cloudflare Workers | TypeScript + D1 (SQLite) |
| 推送 | Apple APNs | HTTP/2 + JWT 签名 |

---

## 四、API 接口清单

Base URL: `https://api.padap.cn`

| 路径 | 方法 | 用途 |
|------|------|------|
| `/heartbeat` | POST | 心跳上报 |
| `/care` | POST / DELETE | 创建/删除关心关系 |
| `/cared-status` | POST | 批量查询被关心者状态 |
| `/cared-by-me` | GET | 查询关注我的人 |
| `/alert` | POST | 上报空闲告警 |
| `/alert/cancel` | POST | 取消告警 |
| `/pending-alerts` | GET | 拉取待处理告警 |
| `/greeting` | POST | 发送问安 |
| `/greeting/reply` | POST | 回复问安 |
| `/pending-greetings` | GET | 拉取未回问安 |

---

## 五、数据模型

### 5.1 客户端本地

| 数据 | 存储位置 |
|------|----------|
| deviceId | iOS: Keychain / Android: ANDROID_ID 派生 |
| 昵称 | 本地 UserDefaults / SharedPreferences |
| 关心关系 | 本地 UserDefaults / SharedPreferences JSON |

### 5.2 服务端（D1 数据库）

- `users` — 用户表（deviceId, lastActiveTime, isCharging, lastCity, onlineStatus, deviceToken）
- `care_relations` — 关心关系（from_device_id, to_code）
- `alerts` — 告警事件（user_id, care_code, alertType, idleMinutes, isResolved）
- `greetings` — 问安消息（from_device_id, to_code, message, reply, notified）

---

## 六、非功能需求

| 类别 | 要求 |
|------|------|
| 隐私 | 无手机号注册，昵称只存本地，关心码不可反推 |
| 卸载重装 | 关心码不变（Keychain / ANDROID_ID） |
| iOS 后台 | 位置/运动/通知三种后台模式 |
| Android 后台 | WorkManager 兜底轮询，无常驻前台通知 |
| 网络 | 全部请求 HTTPS，超时 10 秒 |
| 离线检测 | 服务端 Cron 每 5 分钟检测超过 24 小时无心跳用户 |
| 问安去重 | `notified` 标志位 + 客户端通知 ID 去重 |
