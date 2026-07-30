# 技术规范与约束

## 一、平台技术栈

| 终端 | 技术 | 说明 |
|------|------|------|
| iOS App | Swift 原生 | Swift 6 + SwiftUI，最低 iOS 17.0 |
| Android App | Flutter | Dart 语言，跨平台一致 UI |
| Web 管理端 | Node.js | 服务端渲染或 API 服务 |
| Web Server | Cloudflare Workers | TypeScript，边缘计算部署 |

## 二、部署环境

| 组件 | 平台 | 说明 |
|------|------|------|
| 计算 | Cloudflare Workers | 全球边缘节点，低延迟 |
| 数据库 | Cloudflare D1 | SQLite 兼容分布式数据库 |
| 定时任务 | Workers Cron Trigger | 离线检测 Watchdog |
| 推送 | 极光推送 (JPush) | Android 推送通道，免费额度内 |

## 三、隐私与安全约束

### 3.1 禁止采集的个人身份数据

- ❌ 设备 UDID / IMEI / 序列号
- ❌ 手机号码
- ❌ 身份证号 / 真实姓名
- ❌ 精确 GPS 坐标（服务端不存储）
- ❌ 通讯录 / 相册 / 短信

### 3.2 允许采集的数据

- ✅ 系统生成的 UUID（作为匿名设备标识）
- ✅ SHA-256 哈希派生的 6 位关心码（不可反推）
- ✅ IP 归属城市（Cloudflare 边缘获取，不存精确 IP）
- ✅ 设备活动状态（是否充电、最后一次活跃时间）
- ✅ 用户自定义昵称（仅存本地，不上传服务端）

### 3.3 数据传输

- 全程 HTTPS 加密
- 无明文传输用户敏感信息
- 关心关系仅通过 6 位哈希码关联，不暴露设备 ID

## 四、技术约束

| 约束 | 说明 |
|------|------|
| 无 FCM | Android 端不使用 Firebase Cloud Messaging，告警通过轮询拉取 |
| 无第三方推送 SDK | iOS 仅使用 APNs 直连，不引入极光/个推等三方服务 |
| 无用户注册 | 无需手机号/邮箱注册，纯设备标识 |
| 关心码不可反推 | SHA-256 单向哈希，服务端无法从关心码还原 deviceId |
| 服务端成本 | 不引入独立服务器，全部基于 Cloudflare 免费/低成本方案 |

## 五、兼容性

| 平台 | 最低版本 |
|------|----------|
| iOS | 17.0+ |
| Android | 8.0 (API 26)+ |
| Web | Chrome/Firefox/Safari 最新 2 个版本 |
