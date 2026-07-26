## ADDED Requirements

### Requirement: 多段监测时段配置

系统 SHALL 允许用户配置零段或多段监测活动时段。每段时间段包含开始时间（小时:分钟）、结束时间（小时:分钟）和可选标签。默认为一段"全天"（00:00–23:59）。

#### Scenario: 默认配置
- **WHEN** 用户首次安装 APP 且未配置过监测时段
- **THEN** 系统使用默认监测时段 00:00–23:59（全天候监测）

#### Scenario: 添加监测时段
- **WHEN** 用户在系统配置界面点击"添加监测时段"
- **THEN** 系统添加一段新的监测时段，默认值 09:00–18:00，标签为空

#### Scenario: 删除监测时段
- **WHEN** 用户删除某段监测时段
- **THEN** 该时段从配置列表中移除，若删除后列表为空则等同于全天候监测

#### Scenario: 修改监测时段
- **WHEN** 用户修改某段监测时段的开始/结束时间或标签
- **THEN** 新的配置立即持久化到 SharedPreferences

### Requirement: 旧数据迁移

当 APP 升级后首次启动时，若存在旧的作息配置（`wakeHour`/`sleepHour`）且不存在新的监测时段配置，系统 SHALL 自动将旧配置转换为一段监测时段。

#### Scenario: 旧数据存在且新数据不存在
- **WHEN** SharedPreferences 中存在 `anhao.spike.wakeHour=7`、`anhao.spike.sleepHour=22` 且 `anhao.spike.monitoringWindows` 不存在
- **THEN** 系统自动生成监测时段 07:00–22:00，删除旧键，写入新键

#### Scenario: 新数据已存在
- **WHEN** `anhao.spike.monitoringWindows` 已存在
- **THEN** 系统跳过迁移，直接使用现有配置

### Requirement: 空闲告警仅在监测时段内触发

系统 SHALL 仅当当前时间落入任一监测时段内时，才检查空闲超过阈值并触发告警。

#### Scenario: 当前时间在监测时段内
- **WHEN** 当前时间 10:30 且存在监测时段 08:00–12:00，空闲时长超过阈值
- **THEN** 系统触发空闲告警

#### Scenario: 当前时间不在任何监测时段内
- **WHEN** 当前时间 22:30 且所有监测时段均不覆盖该时间
- **THEN** 系统不触发空闲告警，直接返回

#### Scenario: 跨日监测时段
- **WHEN** 监测时段为 22:00–次日 06:00，当前时间 23:30 且空闲超过阈值
- **THEN** 系统触发空闲告警

### Requirement: ACTIVITY_RECOGNITION 权限声明与引导

系统 SHALL 在 Manifest 中声明 `android.permission.ACTIVITY_RECOGNITION` 权限，并在首次启动时通过运行时权限请求向用户展示授权对话框。若用户拒绝，Motion 监测器启动失败时 SHALL 在 UI 中提示用户授予该权限。

#### Scenario: 首次启动权限请求
- **WHEN** 用户首次安装并启动 APP
- **THEN** 系统弹出权限请求对话框，包含"身体活动"（ACTIVITY_RECOGNITION）

#### Scenario: 用户拒绝权限
- **WHEN** 用户拒绝 ACTIVITY_RECOGNITION 权限
- **THEN** Motion 监测器启动失败，日志记录 `Activity detection usage requires the ACTIVITY_RECOGNITION permission`，但 APP 其他监测器正常运行
