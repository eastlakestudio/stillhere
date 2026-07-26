## Why

StillHere（"还在"）是独居安全监控 App，`idea.md` 原方案用 Laf + Timer 定时心跳。但存在两个致命缺陷：

1. **成本失控**：Laf（laf.run）已无免费额度，打破项目"零成本"的核心目标；国内可访问 + 内置数据库 + 免备案的免费组合在调研后确认不存在。
2. **iOS 心跳不可行**：`idea.md` 的 `Timer.scheduledTimer` 在 App 切后台约 30 秒后被 iOS 墓碑机制挂起，心跳根本发不出去，watchdog 必然误报。

必须在开发启动前锁定一个既免费、又契合 iOS 后台限制的技术基线。

## What Changes

- **后端迁移**：从 Laf（MongoDB）迁移到 Cloudflare Workers + D1（SQLite）+ Cron Triggers，享受永久免费额度（10 万请求/天）+ 内置数据库 + 原生定时调度
- **APNs 重写**：从 npm `apn` 包（依赖 Node TLS）改为 HTTP/2 Provider API，用 Web Crypto API 签 ES256 JWT，解耦于 Node 运行时，未来可跨平台迁移
- **iOS 监测范式转换**：从 Timer 定时推心跳改为**事件驱动被动上报**——系统在用户活动（位置变化/运动状态/充电/前台）时唤醒 App，App 上报"我还活着"，长时间无上报即视为异常
- **watchdog 逻辑调整**：从"检查心跳上报超时"改为"检查最后活动上报时间超时"，并新增分时段阈值（夜间放宽至 8 小时）与充电豁免
- **域名**：从 Laf 子域名改为自有域名托管到 Cloudflare（`workers.dev` 在国内被墙）
- **数据模型修补**：补 `created_at`/`bind_at` 字段，`users` 表预留 `api_token` 字段供产品化阶段启用鉴权
- **鉴权策略**：MVP 阶段不做 API 鉴权（务实优先跑通主链路），架构预留升级路径
- **Worker 组织**：前期单 Worker + 路由分发，产品化阶段按能力域拆分多 Worker

## Capabilities

### New Capabilities

- `activity-reporting`: 被监控人活动上报能力，含 iOS 事件驱动监测（SLC 显著位置变化 + CoreMotion Activity 状态 + BGAppRefreshTask + 充电状态）与后端 heartbeat 端点，是整个安全闭环的输入侧
- `contact-pairing`: 紧急联系人绑定能力，含 6 位数字码/二维码生成（5 分钟有效）、扫码或手输绑定、bind_codes 集合管理，建立被监控人与关注人的关联关系
- `timeout-watchdog`: 超时看门狗能力，含 Cron 定时巡检、分时段阈值判定、充电豁免规则、APNs HTTP/2 告警推送、告警状态机（防重复骚扰 + 恢复解除）

### Modified Capabilities

无（本项目为全新建设，`openspec/specs/` 下尚无既有 spec）。

## Impact

- **新增 `server/` 目录**：Cloudflare Workers 项目，含 `wrangler.toml`（Cron + D1 绑定）、`src/`（路由 + 云函数 + APNs lib）、`schema.sql`（D1 建表）
- **新增 `ios/` 目录**：Swift/SwiftUI App，复用 `idea.md` 的 `APIService`/`BindingView`（仅改 baseURL），重写 `MotionTracker` 为事件驱动监测器，新增 `LocationMonitor`/`MotionActivityMonitor` 等模块
- **外部依赖**：Cloudflare 账号 + 自有域名 + Apple Developer 账号（$99/年，唯一硬成本）
- **关键前置 spike**：实测 CoreMotion `CMMotionActivityManager` 在 iOS 后台的回调可靠性（决定第 1 层是双信号还是 SLC 单信号），1 天可验证
- **`idea.md` 处理**：保留作为原始参考文档，不再作为实施依据
