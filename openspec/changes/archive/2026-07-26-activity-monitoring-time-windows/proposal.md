## Why

当前监测活动时间的配置采用"排除法"——用户分别设置睡眠时段和工作免打扰时段，其余时间视为监测时段。这种模型存在两个问题：1) 语义反直觉（设"不监测"而非"监测"），新增排除项需要不断叠加；2) 无法灵活添加多个监测时段（如晨间活动 + 晚间活动）。同时，`ACTIVITY_RECOGNITION` 权限虽已在 Manifest 声明和运行时请求列表中，但首次安装后用户未授予会导致 Motion 监测器持续报错。

## What Changes

- **BREAKING**：将"作息设置"（睡眠 + 工作免打扰）替换为"监测活动时间"——用户直接配置**哪些时段需要监测**，支持多段
- 每段监测时间包含：开始时间（小时:分钟）、结束时间（小时:分钟）、可选标签
- 空闲告警逻辑改为：当前时间落入任一监测时段内才检查
- 旧数据迁移：首次升级时将原有 `wakeHour`/`sleepHour` 自动转换为一段监测时段
- 权限侧：确认 `ACTIVITY_RECOGNITION` 已在 Manifest 和运行时权限请求中声明，补充权限被拒绝后的引导提示

## Capabilities

### New Capabilities
- `monitoring-time-windows`：可配置的多段监测活动时间管理，替代原有的睡眠/工作免打扰排除法模型

### Modified Capabilities
<!-- 无现有 spec 需要修改 -->

## Impact

- **MonitorManager.kt**：废弃 `wakeHour`/`sleepHour`/`workStartHour`/`workEndHour`，新增 `monitoringWindows: List<TimeWindow>` 及 `isInMonitoringWindow()` 逻辑
- **ConfigScreen.kt**：重写"作息设置"卡片为"监测活动时间"，支持多段时间段的增删改
- **SharedPreferences**：新增 `anhao.spike.monitoringWindows` 键存储 JSON 数组，旧键保留用于数据迁移
- **MainActivity.kt**：`ACTIVITY_RECOGNITION` 已在权限列表（第28行），无需修改
