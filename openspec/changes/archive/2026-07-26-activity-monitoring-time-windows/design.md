## Context

当前 Android 客户端使用"排除法"管理监测时段：通过 `wakeHour`/`sleepHour`/`workStartHour`/`workEndHour` 四个整点参数定义需要**跳过**的时段。剩余时段自动视为监测时段。空闲告警逻辑在 `checkLocalAlert()` 中通过 `isHourInPeriod()` 判断当前小时是否落入排除区间。

问题：
1. 语义不符合直觉——用户想的是"我什么时候需要监测"，不是"我什么时候不需要"
2. 只支持整点（小时粒度），不支持分钟级精度
3. 扩建困难——加一个"午休免打扰"就需要再加两个参数
4. SharedPreferences 中参数散落，扩展性差

目标设备：Android 12+ (API 31+)，仅"安好"版（被监测方）。

## Goals / Non-Goals

**Goals:**
- 将监测时段配置从"排除法"切换为"包含法"——用户直接定义哪些时段需要活动监测
- 支持多段监测时段，每段精确到小时:分钟
- 兼容旧数据：首次升级时自动将旧配置转换为一段监测时段
- 空闲告警逻辑改为仅在监测时段内检查
- ACTIVITY_RECOGNITION 权限已正确声明，确保权限引导到位

**Non-Goals:**
- 不改变空闲告警阈值逻辑（`idleAlertMinutes` 保持不变）
- 不改变告警去重机制（5分钟窗口保持不变）
- 不改变心跳上报机制
- 不涉及 iOS 端

## Decisions

### 1. 数据模型：`TimeWindow` 列表存储为 JSON

```kotlin
data class TimeWindow(
    val startHour: Int,    // 0-23
    val startMinute: Int,  // 0-59
    val endHour: Int,
    val endMinute: Int,
    val label: String = "" // 可选标签，如"晨间""晚间"
)
```

**Rationale**: JSON 数组天然支持多段，SharedPreferences 单键存储，易于序列化/反序列化。Kotlin 的 `kotlinx.serialization` 或 `Gson` 可直接使用（项目已依赖 Gson）。

**Alternative considered**: 用多个 `anhao.spike.window.N.start/end` 键逐个存储。**Rejected**——键数量不固定，删除/插入顺序维护复杂。

### 2. 旧数据迁移策略

首次读取时检测旧键 (`anhao.spike.wakeHour`, `anhao.spike.sleepHour`) 是否存在且新键 (`anhao.spike.monitoringWindows`) 不存在 → 自动转换：

- `wakeHour` → `sleepHour` 转为一段监测时段（例如 7:00 → 22:00）
- `workStartHour` → `workEndHour` 作为第二段排除区间不纳入监测时段
- 迁移后删除旧键，写入新 JSON 数组

**Rationale**: 透明升级，用户无感知。`wakeHour`/`sleepHour` 是原始含义（清醒 = 监测），转换逻辑直观。

### 3. 时段判断逻辑

`isInMonitoringWindow()`: 遍历 `monitoringWindows` 列表，将当前 `HH:MM` 与每段的 `startHour:startMinute` → `endHour:endMinute` 比较。支持跨日时段（如 22:00 → 次日 7:00）。

**Alternative**: 转为分钟数比较。`startMinutes = startHour * 60 + startMinute`。简化跨日判断：若 `endMinutes < startMinutes` 则跨日。

### 4. UI 交互：ConfigScreen 内联增删

- 每段时间段显示为一行：`[标签] 08:00 — 12:00 [删除]`
- 底部"＋ 添加监测时段"按钮，弹出时间选择器（或内联展开编辑行）
- 默认提供一段"全天"（0:00–23:59）作为兜底

## Risks / Trade-offs

- **[兼容性]** 旧用户升级后只保留从 `wakeHour`–`sleepHour` 转换的一段，丢失了原本"工作免打扰"排除的中间段 → **Mitigation**: 这是预期行为，新模型下用户可自行添加多段
- **[复杂度]** 从 4 个整型参数变为 JSON 数组，序列化出错时需降级 → **Mitigation**: 提供默认值 `[TimeWindow(0, 0, 23, 59, "全天")]` 确保永远有有效配置
