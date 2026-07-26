## Context

StillHere（"还在"）是面向独居老人的安全监控 App：被监控人手机持续上报"我还活着"信号，服务器 watchdog 在超时未上报时通过 APNs 推送告警给紧急联系人。

`idea.md` 原方案采用 Laf（laf.run）+ MongoDB + npm `apn` 包 + iOS `Timer.scheduledTimer` 心跳。经调研发现两个阻断性问题：

1. **Laf 已无免费额度**，且"国内可访问 + 内置数据库 + 免备案 + 零成本"四要素在主流 Serverless 平台（Laf/Supabase/Cloudflare/腾讯 SCF/阿里 FC）中无法同时满足
2. **iOS `Timer.scheduledTimer` 在后台约 30 秒后被墓碑机制挂起**，心跳方案在 iOS 上物理不可行

必须在开发启动前锁定既免费、又契合 iOS 后台限制的技术基线。本设计确立该基线并预埋产品化演进路径。

关键约束：
- 零月费成本（仅接受 Apple Developer $99/年 + 域名 ~50 元/年）
- 国内可访问（App 用户为国内独居老人）
- 心跳场景对延迟不敏感（15 分钟一次，200ms 延迟无影响）
- iOS 后台执行受系统严格管控
- MVP 务实优先，但架构需支持平滑演进到产品级

## Goals / Non-Goals

**Goals:**
- 锁定 Cloudflare Workers + D1 作为后端，零月费且契合需求
- 建立 iOS 事件驱动监测范式，替代不可行的 Timer 心跳
- 定义 D1 数据模型，预留产品化演进字段
- 定义 APNs HTTP/2 推送的实现路径
- 预埋单 Worker → 多 Worker、无鉴权 → 有鉴权的演进路径

**Non-Goals:**
- MVP 不实现 Silent Push 主动探测（受节流限制，留作 V2）
- MVP 不实现 API 鉴权（架构预留，产品化启用）
- 不开发 Android 版本
- 不支持多紧急联系人（先单联系人，`contact_id` 单字段）
- 不做实时位置追踪（仅用 SLC 作为活动信号，不暴露坐标）
- 不做端到端加密（MVP 明文传输，产品化再评估）

## Decisions

### 决策 1：后端平台选 Cloudflare Workers + D1 + Cron Triggers

**选择**：Cloudflare Workers（V8 isolate）+ D1（SQLite）+ Cron Triggers。

**理由**：
- 永久免费额度 10 万请求/天，"还在"日均调用 <1 万次，绰绰有余
- D1 内置 SQLite 数据库，免外接
- Cron Triggers 原生支持定时调度（watchdog 每 5 分钟）
- 自有域名托管后免备案，`workers.dev` 国内虽被墙但自定义域名可用
- 心跳场景对延迟不敏感（15 分钟一次，国内 50-200ms 延迟可接受）

**考虑的替代方案**：
| 方案 | 淘汰原因 |
|------|---------|
| Laf (laf.run) | 已无免费额度，打破零成本目标 |
| Supabase | 海外节点国内访问延迟高，对老人 App 是硬伤 |
| 腾讯云 SCF / 阿里云 FC | 无内置数据库需外接，API 网关常需备案域名，阿里 FC 仅 3 个月试用 |
| Firebase | 国内被墙 |

### 决策 2：APNs 推送用 HTTP/2 Provider API + Web Crypto 签 JWT

**选择**：放弃 npm `apn` 包，改用 Cloudflare Workers 的 Web Crypto API 自实现 APNs HTTP/2 推送。

**理由**：
- Workers 的 V8 isolate 不支持 Node.js TLS 长连接，`apn` 包无法运行
- APNs 官方 HTTP/2 Provider API（`https://api.push.apple.com/3/device/{token}`）可通过 `fetch` 调用
- Web Crypto API 原生支持 ECDSA P-256（ES256）和 `subtle.importKey('pkcs8', ...)`
- `.p8` 私钥存为 Workers Secret（`APNS_P8_KEY`），运行时注入不泄露到代码

**实现要点**：
- JWT header: `{"alg":"ES256","kid":"<KeyID>","typ":"JWT"}`
- JWT payload: `{"iss":"<TeamID>","iat":<timestamp>}`
- 请求头：`authorization: bearer <jwt>`、`apns-topic: <bundle_id>`、`apns-push-type: alert`
- 此改造使后端解耦于 Node 运行时，未来可迁移到任何支持 Web Crypto 的平台

### 决策 3：iOS 监测从 Timer 定时推改为事件驱动被动上报

**选择**：放弃 `Timer.scheduledTimer` 心跳，改用系统在用户活动事件发生时唤醒 App 上报"我还活着"。

**理由**：
- iOS 墓碑机制使后台 Timer 30 秒后失效，原方案物理不可行
- 事件驱动契合 iOS 后台 API 设计哲学（系统只在特定事件时唤醒 App）
- 关键洞察："还在"真正要检测的是"人还在不在动"，而非"App 在不在跑"；长时间无事件 = 长时间无上报 = 异常，恰好是要检测的信号

**三层防御架构**：
```
L1 主信号（被动活动检测，低功耗）
  ├─ SLC 显著位置变化（CLLocationManager.startMonitoringSignificantLocationChanges）
  │   基站切换/500m+移动唤醒，App 被杀也能唤醒，需 always 定位权限
  └─ CoreMotion Activity（CMMotionActivityManager.startActivityUpdates）
      走/跑/静/驾车状态变化，配 UIBackgroundModes: motion，无蓝条
      ⚠ 后台能力有争议，需 spike 验证

L2 兜底（系统给的偶尔机会）
  ├─ BGAppRefreshTask（系统智能调度，时机不可控，不活跃 App 几乎不调度）
  └─ 充电状态变化（UIDevice.batteryStateDidChangeNotification，App 被杀收不到）

L3 主动探测（V2，MVP 不做）
  └─ Silent Push（服务器超时临近时主动唤醒，受节流限制，每小时 >3-4 次被丢弃）
```

**考虑的替代方案**：
| 方案 | 淘汰原因 |
|------|---------|
| 持续后台定位 | 蓝色状态栏，耗电，App Store 审核风险 |
| 静音音频保活 | 灰色地带，审核高风险 |
| VoIP Push (PushKit) | 最可靠但非 VoIP App 会被 App Store 拒 |

### 决策 4：Worker 组织——前期单 Worker + 路由分发

**选择**：MVP 用单 Worker，`fetch` 处理 HTTP 路由 + `scheduled` 处理 Cron，产品化后按能力域拆分。

**理由**：
- MVP 规模小（4 个端点 + 1 个 cron），单 Worker 部署/配置最简
- Cron Triggers 与 Worker 绑定，单 Worker 内 cron 与路由共享 D1 绑定
- 拆分预埋：路由用统一 dispatcher，拆分时只动 `wrangler.toml` + 路由表，业务逻辑不变

**演进路径**：
```
MVP：单 Worker
  fetch: /heartbeat /generate-bind-code /bind-user
  scheduled: watchdog

产品化：多 Worker（按能力域隔离）
  heartbeat-worker（+ API key 中间件）
  binding-worker（+ API key 中间件）
  watchdog-worker（内部 cron 触发）
  共享 lib/apns.ts 模块
```

### 决策 5：watchdog 阈值——分时段 + 充电豁免

**选择**：放弃固定 120 分钟阈值，改为分时段阈值 + 充电中豁免。

**理由**：
- 固定 120 分钟阈值在老人睡觉时（8 小时不动）必然误报
- 夜间（22:00-08:00）放宽至 8 小时
- 白天默认 2 小时
- 充电中（`is_charging` 信号）再放宽——睡觉常充电，充电是"在家安全"的弱信号

**阈值判定逻辑**：
```
effective_threshold = base_threshold
if 当前时间在安静时段(22:00-08:00): effective_threshold = night_threshold  # 默认 8h
if 用户上报充电中: effective_threshold *= 2  # 充电豁免
告警条件: (now - last_active_time) > effective_threshold AND NOT is_alerted
```

### 决策 6：鉴权——MVP 无 + 预留 api_token

**选择**：MVP 阶段不做 API 鉴权，`users` 表预留 `api_token` 字段，产品化启用。

**理由**：
- MVP 务实优先跑通主链路，鉴权不阻塞核心闭环
- `api_token` 字段留空即可，启用时补中间件校验，架构不返工
- 产品化阶段风险评估：`bind-user` 接口需限流防撞码（6 位码空间 90 万）

### 决策 7：数据模型——D1 SQLite 关系表

**选择**：MongoDB 文档模型改为 SQLite 关系表，并补 `idea.md` 缺失字段。

**users 表**：
```sql
CREATE TABLE users (
  id TEXT PRIMARY KEY,                  -- userId (UUID, 客户端 Keychain 生成)
  device_token TEXT,                    -- APNs Device Token
  last_active_time INTEGER,             -- 最后活动上报时间(ms)
  threshold_minutes INTEGER DEFAULT 120,-- 白天阈值(分钟)
  contact_id TEXT,                      -- 紧急联系人 userId
  is_alerted INTEGER DEFAULT 0,         -- 是否已告警(0/1)
  is_charging INTEGER DEFAULT 0,        -- 是否充电中(客户端上报)
  api_token TEXT,                       -- 预留鉴权 token(MVP 留空)
  created_at INTEGER,                   -- 注册时间(★ idea.md 缺失)
  bind_at INTEGER                       -- 绑定关系建立时间(★ idea.md 缺失)
);
CREATE INDEX idx_users_contact ON users(contact_id);
CREATE INDEX idx_users_pending ON users(contact_id)
  WHERE contact_id IS NOT NULL AND is_alerted = 0;
```

**bind_codes 表**：
```sql
CREATE TABLE bind_codes (
  code TEXT PRIMARY KEY,                -- 6 位数字码
  user_id TEXT NOT NULL,                -- 被监控人 userId
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL           -- 创建后 5 分钟
);
CREATE INDEX idx_bind_user ON bind_codes(user_id);
CREATE INDEX idx_bind_expires ON bind_codes(expires_at);
```

### 决策 8：watchdog 查询优化

**选择**：放弃 `idea.md` 的全表扫描，改用条件查询。

**理由**：`db.collection('users').get()` 在用户量上千时会超 D1 免费额度/超时。

```sql
-- 只查"可能超时且未告警且已绑定联系人"的用户
SELECT * FROM users
WHERE contact_id IS NOT NULL
  AND is_alerted = 0
  AND last_active_time < ?   -- 当前时间减去阈值
```

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| Cloudflare 国内访问偶发不稳（运营商抽风） | 心跳场景容忍延迟；watchdog 在服务端 cron 跑不依赖客户端；产品化可加国内备用 |
| CoreMotion 后台能力有争议（两份资料矛盾） | **前置 spike 实测**（1 天验证），不通过则降级为 SLC 单信号 |
| SLC 需"始终使用定位"权限，用户授权门槛高 | 首次引导时清楚解释用途；权限被拒则 App 功能受限需明示 |
| 老人睡觉 8 小时不触发 SLC/Activity 导致误报 | 分时段阈值（夜间 8h）+ 充电豁免 |
| Silent Push 节流（不活跃 App 被完全节流） | MVP 不依赖 Silent Push；V2 启用时仅超时临近发 1 条 |
| BGAppRefreshTask 对不活跃 App 几乎不调度 | 作为 L2 兜底，不依赖；主信号是 SLC |
| `bind-user` 6 位码撞库风险 | 绑定成功即刻销毁 bind_code；产品化加限流 |
| 手机关机/没电/掉水 | 事件驱动模型自然处理：零上报 = 超时 = 告警 |
| workers.dev 国内被墙 | 必须用自定义域名托管 Cloudflare（用户已有域名） |

## Migration Plan

本变更为全新项目搭建，无既有代码迁移。从 `idea.md` 参考代码到目标实现的映射：

| `idea.md` 组件 | 目标实现 | 改动量 |
|----------------|---------|--------|
| Laf 云函数 ×4 | Cloudflare Workers 单 Worker 路由 | 重写（MongoDB 语法→SQL） |
| npm `apn` 包 | Web Crypto + fetch HTTP/2 | 重写（新模块） |
| Laf cron 触发器 | wrangler.toml Cron Triggers | 配置迁移 |
| Laf MongoDB | D1 SQLite | schema 重设计（见决策 7） |
| `APIService.swift` | 复用，仅改 `baseURL` | 极小 |
| `BindingView.swift` | 复用 | 无 |
| `AppDelegate.swift` | 复用 APNs 注册 | 无 |
| `MotionTracker.swift`（Timer） | 重写为事件驱动监测器 | 重写 |
| —（新增） | `LocationMonitor`/`MotionActivityMonitor`/`ChargingMonitor` | 新增 |

## Open Questions

1. **CoreMotion 后台能力实测结果**（前置 spike）：决定 L1 是 SLC + CoreMotion 双信号还是 SLC 单信号。spike 方法：写最小 demo App，配 `UIBackgroundModes: motion`，锁屏 1 小时观察 `startActivityUpdates` 是否回调。
2. **Cloudflare 在目标用户网络环境的实际稳定性**：需在老人手机网络下实测自定义域名可达性。
3. **APNs HTTP/2 在 Workers fetch 的兼容性**：需验证 `fetch` 到 `api.push.apple.com` 是否走 HTTP/2，以及 Web Crypto `importKey('pkcs8')` 对 `.p8` PKCS#8 格式的解析。
4. **`BGContinued` / 通配符标识符（WWDC 2025 提到的 iOS 26 新 API）**：是否对"还在"有额外机会窗口，待 iOS 26 正式版文档明确。
