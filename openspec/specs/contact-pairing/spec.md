# contact-pairing Specification

## Purpose
TBD - created by archiving change migrate-to-cloudflare-workers. Update Purpose after archive.
## Requirements
### Requirement: 绑定码生成

被监控人 App SHALL 通过 `POST /generate-bind-code` 请求生成绑定码。后端 SHALL 生成 6 位纯数字绑定码（`Math.floor(100000 + Math.random() * 900000)`），写入 D1 `bind_codes` 表，有效期 5 分钟（`expires_at = createdAt + 5*60*1000`）。生成新码前 SHALL 清除该 `userId` 的历史未使用绑定码。

后端 SHALL 返回 `{ code: 0, data: { bindCode, qrContent, expiresAt } }`，其中 `qrContent` 为 JSON 字符串 `{ action: "bind", code, userId }`，供关注人 App 扫码解析。

#### Scenario: 生成新绑定码

- **WHEN** 被监控人 App 调用 `POST /generate-bind-code` 携带 `{ userId }`
- **THEN** 系统清除该 `userId` 的历史未消费绑定码，生成新 6 位码写入 `bind_codes`（含 `created_at`、`expires_at`），返回绑定码与二维码内容

#### Scenario: 缺少 userId 拒绝

- **WHEN** 收到 `POST /generate-bind-code` 但 `userId` 为空
- **THEN** 系统返回 `{ code: 400, message: "缺少 userId" }`

### Requirement: 二维码展示

被监控人 App SHALL 将后端返回的 `qrContent` 字符串用 CoreImage 原生 `CIFilter.qrCodeGenerator()` 生成二维码 `UIImage` 展示，同时展示 6 位数字码供关注人手输。SHALL 展示过期倒计时或刷新按钮。

#### Scenario: 展示二维码与数字码

- **WHEN** 被监控人进入绑定页且成功获取绑定码
- **THEN** App 展示二维码图片与 6 位数字码，并提供"刷新绑定码"按钮

### Requirement: 扫码或手输绑定

关注人 App SHALL 通过扫描被监控人的二维码解析出 `{ code, userId }`，或手动输入 6 位数字码，调用 `POST /bind-user` 携带 `{ followerId, bindCode }` 完成绑定。后端 SHALL 校验绑定码有效（存在且未过期），建立被监控人 → 关注人的关联（更新被监控人 `users.contact_id = followerId` 与 `bind_at` 时间戳），绑定成功后立即销毁该 `bind_code` 记录防撞码。

#### Scenario: 扫码绑定成功

- **WHEN** 关注人扫描被监控人二维码，后端校验 `bindCode` 在 `bind_codes` 表存在且 `expires_at > now`
- **THEN** 系统将被监控人 `users.contact_id` 设为关注人 `followerId`，写入 `bind_at`，删除该 `bind_code` 记录，返回 `{ code: 0, message: "成功绑定关注对象！" }`

#### Scenario: 手输码绑定成功

- **WHEN** 关注人手输 6 位数字码，后端校验码有效
- **THEN** 系统执行与扫码相同的绑定流程

#### Scenario: 绑定码无效或已过期

- **WHEN** 收到 `POST /bind-user` 但 `bindCode` 在 `bind_codes` 表不存在或 `expires_at <= now`
- **THEN** 系统返回 `{ code: 404, message: "绑定码无效或已过期，请刷新后再试" }`

#### Scenario: 绑定码已被消费

- **WHEN** 绑定码已被前一次成功绑定消费（记录已删除）
- **THEN** 系统返回 `{ code: 404, message: "绑定码无效或已过期，请刷新后再试" }`

#### Scenario: 不能绑定自己

- **WHEN** 收到 `POST /bind-user` 且 `bindCode` 对应的 `user_id` 等于 `followerId`
- **THEN** 系统返回 `{ code: 400, message: "不能绑定自己" }`

#### Scenario: 参数不完整

- **WHEN** 收到 `POST /bind-user` 但 `followerId` 或 `bindCode` 为空
- **THEN** 系统返回 `{ code: 400, message: "参数不完整" }`

### Requirement: 绑定关系单一性

MVP 阶段，一个被监控人 SHALL 仅能绑定一个紧急联系人（`contact_id` 单字段）。重新绑定新联系人时，新绑定 SHALL 覆盖旧 `contact_id`。多联系人支持为 Non-Goal，留作产品化阶段扩展。

#### Scenario: 重新绑定覆盖旧联系人

- **WHEN** 被监控人已有 `contact_id`，再次通过新绑定码被新关注人绑定
- **THEN** 系统 `contact_id` 更新为新关注人 `followerId`，`bind_at` 更新为当前时间，旧关注人不再收到告警

