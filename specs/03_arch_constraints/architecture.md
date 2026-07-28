# 架构约束

## 一、整体架构

### 1.1 推送架构

```
┌──────────┐  心跳/轮询  ┌──────────────────┐  Web Crypto/HTTP2  ┌──────────┐
│ iOS App  │ ──────────→ │ Cloudflare Workers │ ────────────────→ │ Apple APNs│
│ (Swift)  │ ←── APNs ── │   (api.padap.cn)   │                    └──────────┘
└──────────┘              └────────┬──────────┘
                                   │
┌──────────┐  轮询拉取     ┌───────▼──────┐
│ Android  │ ────────────→ │  D1 数据库    │
│ (Flutter)│ ←── 本地通知  │  (SQLite)     │
└──────────┘              └──────────────┘
```

- iOS: APNs 远程推送（服务端直接 HTTP/2 + JWT 签名连接 Apple）
- Android: 无 FCM 推送通道，通过 WorkManager 轮询拉取 + 本地通知
- 服务端不依赖任何第三方推送 SDK

### 1.2 分层架构

| 层 | 技术 | 说明 |
|----|------|------|
| 客户端 | iOS Swift / Android Flutter | 原生 UI + 本地传感器 |
| 网关 | Cloudflare Workers | HTTPS API 网关 + 边缘计算 |
| 数据 | Cloudflare D1 | SQLite 兼容分布式数据库 |
| 推送 | Apple APNs | 仅 iOS 远程推送 |
| 认证 | Google OAuth 2.0 | Dashboard 管理后台 |

## 二、代码与开源

### 2.1 GitHub 开源

- 仓库地址：GitHub 公开仓库
- 许可证：待定（MIT / Apache 2.0）
- 开源范围：iOS、Android、Server 全部源码
- 不包含：APNs .p8 密钥、环境变量、Client Secret 等敏感配置

### 2.2 仓库结构

```
stillhere/
├── ios/                  # iOS 原生 Swift 代码
├── android/              # Android Flutter 代码
├── stillhere-server/     # Cloudflare Workers 服务端
├── doc/                  # 设计文档
├── specs/                # 需求与规范
└── README.md
```

## 三、Web Server 公开功能

### 3.1 公开页面

| 页面 | 路径 | 说明 |
|------|------|------|
| 产品介绍页 | `/` | 公众 Landing Page，介绍产品功能 |
| 隐私条款 | `/privacy` | 隐私政策，说明数据采集和使用范围 |
| 功能说明 | `/features` | 公开的 API 端点说明 |

### 3.2 公开 API

| 路径 | 用途 | 认证 |
|------|------|------|
| `/heartbeat` | 心跳上报 | 无 |
| `/care` | 关心关系管理 | 无 |
| `/cared-status` | 状态查询 | 无 |
| `/alert` | 告警上报 | 无 |
| `/greeting` | 问安 | 无 |

### 3.3 Google SSO 认证

| 配置项 | 值 |
|--------|-----|
| 认证方式 | Google OAuth 2.0 |
| 限定账号 | `mingh.liu@gmail.com` |
| 适用页面 | Dashboard 管理后台、数据管理界面 |
| 登录入口 | `/auth/login` |
| 回调地址 | `/auth/callback` |

## 四、管理后台（需 Google SSO）

| 功能 | 路径 | 说明 |
|------|------|------|
| 数据看板 | `/dashboard` | 用户数、活跃度、告警统计等运营数据 |
| 用户管理 | `/dashboard/users` | 查看用户列表、最后活跃时间 |
| 关系管理 | `/dashboard/relations` | 查看关心关系列表 |
| 告警记录 | `/dashboard/alerts` | 查看历史告警记录 |
| 问安记录 | `/dashboard/greetings` | 查看问安消息 |

## 五、安全约束

- Dashboard 所有路径必须通过 Google SSO 认证
- 仅 `mingh.liu@gmail.com` 有权访问管理后台
- 非授权用户访问 `/dashboard` 重定向到 Google 登录页
- SSO Token 验证在 Worker 中间件层完成
