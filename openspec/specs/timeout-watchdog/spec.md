# timeout-watchdog Specification

## Purpose
TBD - created by archiving change migrate-to-cloudflare-workers. Update Purpose after archive.
## Requirements
### Requirement: 定时巡检触发

后端 watchdog SHALL 通过 Cloudflare Cron Triggers 定时触发（默认每 5 分钟一次，cron 表达式 `0 */5 * * * *`，配置于 `wrangler.toml`）。触发时执行巡检逻辑：查询可能超时的用户并判定告警。

#### Scenario: Cron 触发巡检

- **WHEN** Cron Triggers 按配置间隔触发 watchdog 的 `scheduled` handler
- **THEN** 系统执行一次全量巡检，查询所有"已绑定联系人 + 未告警 + 最后上报时间超阈值"的用户

### Requirement: 巡检查询优化

watchdog SHALL 放弃全表扫描，改用条件查询，仅检索"可能需要告警"的用户，避免用户量增长后超 D1 免费额度或超时。查询条件：`contact_id IS NOT NULL AND is_alerted = 0 AND last_active_time < (now - effective_threshold)`。

#### Scenario: 只查可能超时的用户

- **WHEN** watchdog 执行巡检
- **THEN** 系统只返回已绑定联系人、未告警、且 `last_active_time` 早于当前时间减去有效阈值的用户记录，跳过未绑定或已告警的用户

### Requirement: 分时段阈值判定

watchdog SHALL 根据当前时间（被监控人本地时间或服务器时间）应用分时段阈值，避免老人睡觉时误报。默认阈值规则：

- 白天（08:00-22:00）：`threshold_minutes`（默认 120 分钟）
- 夜间安静时段（22:00-08:00）：`night_threshold_minutes`（默认 480 分钟 / 8 小时）

用户 `threshold_minutes` 字段 SHALL 可被未来扩展为自定义（MVP 用默认值）。

#### Scenario: 白天超时触发告警判定

- **WHEN** 当前时间在 08:00-22:00，用户 `last_active_time` 距今超过 `threshold_minutes`（默认 120 分钟）
- **THEN** 系统判定该用户进入告警流程

#### Scenario: 夜间放宽阈值不误报

- **WHEN** 当前时间在 22:00-08:00（夜间安静时段），用户 `last_active_time` 距今超过白天阈值但未超过夜间阈值（默认 480 分钟）
- **THEN** 系统不触发告警，等待夜间阈值突破

### Requirement: 充电豁免

watchdog SHALL 在用户 `is_charging=1` 时对阈值乘以豁免系数（默认 ×2），反映"充电中=在家安全"的弱信号。豁免后仍超时则照常告警。

#### Scenario: 充电中阈值放宽

- **WHEN** 用户 `is_charging=1` 且 `last_active_time` 距今超过 `threshold_minutes * 2`
- **THEN** 系统判定该用户进入告警流程

#### Scenario: 充电中未超放宽阈值

- **WHEN** 用户 `is_charging=1` 且 `last_active_time` 距今超过原阈值但未超过 `threshold_minutes * 2`
- **THEN** 系统不触发告警

### Requirement: APNs HTTP/2 告警推送

watchdog 判定用户超时且未告警时，SHALL 通过 APNs HTTP/2 Provider API 向该用户绑定的紧急联系人（`contact_id` 对应用户的 `device_token`）推送告警。推送实现 SHALL 使用 Cloudflare Workers 的 Web Crypto API 签 ES256 JWT（用存为 Workers Secret 的 `APNS_P8_KEY` 私钥），通过 `fetch` 调用 `https://api.push.apple.com/3/device/{deviceToken}`，请求头含 `authorization: bearer <jwt>`、`apns-topic: <APP_BUNDLE_ID>`、`apns-push-type: alert`。

推送内容 SHALL 包含标题与正文，明示超时时长与联系人确认安全的指引。

#### Scenario: 超时告警推送成功

- **WHEN** 用户被判超时且 `is_alerted=0`，其 `contact_id` 对应用户有有效 `device_token`
- **THEN** 系统签 JWT，调 APNs HTTP/2 推送告警通知给联系人，推送标题如"⚠️ 告警：还在 安全监控"，正文提示"您关注的对象超过 N 分钟未有活动，请及时联系确认安全"

#### Scenario: 联系人无 deviceToken 跳过

- **WHEN** 用户被判超时但其 `contact_id` 对应用户的 `device_token` 为空
- **THEN** 系统跳过推送（无法送达），但仍标记 `is_alerted=1` 避免重复尝试

#### Scenario: APNs 推送失败重试策略

- **WHEN** APNs HTTP/2 调用返回非 2xx 状态码
- **THEN** 系统记录失败日志，不阻塞当次巡检（下次 Cron 触发会重新判定），不标记 `is_alerted`（允许下次重试）

### Requirement: 告警状态机

`users.is_alerted` 字段 SHALL 作为告警状态机标志，防止重复骚扰与支持恢复解除。状态流转：

- `0 → 1`：判定超时且推送（或推送失败但联系人无 token）后，置 `is_alerted=1`
- `1 → 0`：用户重新上报心跳（`last_active_time` 更新）且当前未超时，置 `is_alerted=0`（恢复解除）

#### Scenario: 防重复告警

- **WHEN** 用户 `is_alerted=1`（已告警），后续巡检即使仍超时
- **THEN** 系统不再向联系人重复推送，避免骚扰

#### Scenario: 恢复活动解除告警

- **WHEN** 用户 `is_alerted=1`，被监控人 App 重新上报心跳使 `last_active_time` 更新到当前时间
- **THEN** 系统置 `is_alerted=0`，恢复监测状态，下次超时可再次告警

### Requirement: 未绑定联系人跳过

watchdog SHALL 跳过 `contact_id IS NULL` 的用户（未绑定紧急联系人），不进入告警判定。

#### Scenario: 未绑定用户不告警

- **WHEN** 用户 `contact_id` 为空（未绑定联系人）
- **THEN** 系统跳过该用户，不判定超时，不推送

