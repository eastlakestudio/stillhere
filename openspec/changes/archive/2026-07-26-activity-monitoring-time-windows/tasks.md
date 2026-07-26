## 1. 数据模型与持久化

- [x] 1.1 创建 `TimeWindow` data class（startHour, startMinute, endHour, endMinute, label）
- [x] 1.2 MonitorManager 新增 `monitoringWindows: List<TimeWindow>` 属性，SharedPreferences JSON 读写
- [x] 1.3 实现 `isInMonitoringWindow(): Boolean` 方法（分钟数比较，支持跨日）
- [x] 1.4 实现旧数据迁移逻辑：检测旧键 → 转换为一段监测时段 → 删除旧键

## 2. 告警逻辑重构

- [x] 2.1 修改 `checkLocalAlert()`：将 `isHourInPeriod` 排除逻辑替换为 `isInMonitoringWindow` 包含逻辑
- [x] 2.2 废弃 `wakeHour`/`sleepHour`/`workStartHour`/`workEndHour` 属性（保留向后兼容但标记 deprecated）
- [x] 2.3 移除 `isHourInPeriod()` 方法

## 3. UI 重构：ConfigScreen

- [x] 3.1 重写"作息设置"卡片为"监测活动时间"，标题和描述文案更新
- [x] 3.2 实现监测时段列表展示：每行显示标签 + 时间范围 + 删除按钮
- [x] 3.3 实现"添加监测时段"按钮和编辑交互（时间选择器或内联编辑）
- [x] 3.4 支持修改已有时间段的开始/结束时间和标签

## 4. 权限验证与引导

- [x] 4.1 确认 `AndroidManifest.xml` 中 `ACTIVITY_RECOGNITION` 声明存在
- [x] 4.2 确认 `MainActivity.requiredPermissions` 包含 `ACTIVITY_RECOGNITION`
- [x] 4.3 Motion 监测器权限被拒时在 UI 中展示提示信息

## 5. 编译与验证

- [x] 5.1 编译 Debug APK 确保无编译错误
- [x] 5.2 全新安装测试：默认全天候监测、权限弹窗、添加/删除时段 ✅ 默认全天候（DEFAULT=00:00-23:59）、权限弹窗已出现、migratedToWindows=true、监测已启动
- [x] 5.3 升级测试：旧配置自动迁移为一段监测时段 ✅ wakeHour=7/sleepHour=22/workStart=9/workEnd=18 → 迁移为 7:00-9:00 + 18:00-22:00，旧键已清除
- [x] 5.4 验证空闲告警仅在监测时段内触发（调整时段后观察日志） ✅ 窗口内告警正常触发，窗口外告警跳过
