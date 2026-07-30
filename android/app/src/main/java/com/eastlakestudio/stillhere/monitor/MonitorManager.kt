package com.eastlakestudio.stillhere.monitor

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eastlakestudio.stillhere.MainActivity
import com.eastlakestudio.stillhere.StillHereApp
import com.eastlakestudio.stillhere.data.Logger
import com.eastlakestudio.stillhere.data.Reporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 监测活动时段
 * @param startHour 开始小时 (0-23)
 * @param startMinute 开始分钟 (0-59)
 * @param endHour 结束小时 (0-23)
 * @param endMinute 结束分钟 (0-59)
 * @param label 可选标签（如"晨间"、"晚间"），为空则仅显示时间
 */
data class TimeWindow(
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 23,
    val endMinute: Int = 59,
    val label: String = ""
) {
    /** 开始时间转为分钟数 (0-1439) */
    val startMinutes: Int get() = startHour * 60 + startMinute
    /** 结束时间转为分钟数 (0-1439) */
    val endMinutes: Int get() = endHour * 60 + endMinute

    /** 检查给定分钟数是否在此窗口内（含起始，不含结束；支持跨日） */
    fun contains(minutes: Int): Boolean {
        return if (endMinutes > startMinutes) {
            minutes in startMinutes until endMinutes
        } else {
            // 跨日（如 22:00–06:00）
            minutes >= startMinutes || minutes < endMinutes
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("sh", startHour)
        put("sm", startMinute)
        put("eh", endHour)
        put("em", endMinute)
        put("l", label)
    }

    companion object {
        fun fromJson(json: JSONObject): TimeWindow = TimeWindow(
            startHour = json.optInt("sh", 0),
            startMinute = json.optInt("sm", 0),
            endHour = json.optInt("eh", 23),
            endMinute = json.optInt("em", 59),
            label = json.optString("l", "")
        )

        val DEFAULT = listOf(TimeWindow(0, 0, 23, 59, ""))
    }
}

/**
 * 监测器总管：管理生命周期 + 周期心跳 + 本地告警
 *
 * 对应 iOS MonitorManager
 */
class MonitorManager(
    val context: Context,
    val logger: Logger
) {

    companion object {
        @Volatile
        var instance: MonitorManager? = null

        /** 周期心跳间隔（毫秒）：约 1 小时 */
        const val HEARTBEAT_INTERVAL_MS = 60 * 60 * 1000L

        /** 默认空闲阈值（分钟） */
        const val DEFAULT_IDLE_MINUTES = 30

        // 旧键名（用于迁移）
        private const val KEY_OLD_WAKE = "anhao.spike.wakeHour"
        private const val KEY_OLD_SLEEP = "anhao.spike.sleepHour"
        private const val KEY_OLD_WORK_START = "anhao.spike.workStartHour"
        private const val KEY_OLD_WORK_END = "anhao.spike.workEndHour"
        private const val KEY_WINDOWS = "anhao.spike.monitoringWindows"
        private const val KEY_MIGRATED = "anhao.spike.migratedToWindows"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anhao.spike.manager", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastReportStatus = MutableStateFlow("等待上报…")
    val lastReportStatus: StateFlow<String> = _lastReportStatus.asStateFlow()

    /** 待确认告警分钟数，null 表示无待确认告警 */
    private val _pendingAlertMinutes = MutableStateFlow<Int?>(null)
    val pendingAlertMinutes: StateFlow<Int?> = _pendingAlertMinutes.asStateFlow()
    private var alertTimerJob: Job? = null

    /** 各监测器开关（默认全部开启） */
    private val _enabledMonitors = MutableStateFlow(setOf("SLC", "Motion", "BGAppRefresh", "Charging", "Foreground", "Alert"))
    val enabledMonitors: StateFlow<Set<String>> = _enabledMonitors.asStateFlow()

    /** 运动感知是否可用（API 未禁用），默认 true，首次失败后标记 */
    private val _motionAvailable = MutableStateFlow(true)
    val motionAvailable: StateFlow<Boolean> = _motionAvailable.asStateFlow()

    fun setMotionAvailable(available: Boolean) {
        _motionAvailable.value = available
    }

    /** 运动感知是否使用加速度计兜底模式 */
    private val _isMotionFallback = MutableStateFlow(false)
    val isMotionFallback: StateFlow<Boolean> = _isMotionFallback.asStateFlow()

    fun setMotionFallback(fallback: Boolean) {
        _isMotionFallback.value = fallback
    }

    // 监测器实例
    private var slc: LocationMonitor? = null
    private var motion: MotionMonitor? = null
    private var charging: ChargingMonitor? = null

    /** 当前充电状态 */
    private var isCharging = false

    /** 最后活动时间（持久化） */
    private var lastActivityTime: Long
        get() {
            val ts = prefs.getLong("anhao.spike.lastActivity", 0)
            return if (ts > 0) ts else System.currentTimeMillis()
        }
        set(value) {
            prefs.edit().putLong("anhao.spike.lastActivity", value).apply()
        }

    /** 上次告警时间（持久化） */
    private var lastAlertTime: Long
        get() = prefs.getLong("anhao.spike.lastAlert", 0)
        set(value) {
            prefs.edit().putLong("anhao.spike.lastAlert", value).apply()
        }

    /** 是否处于告警状态（用于检测活动恢复时取消告警） */
    private var isAlerted: Boolean
        get() = prefs.getBoolean("anhao.spike.isAlerted", false)
        set(value) {
            prefs.edit().putBoolean("anhao.spike.isAlerted", value).apply()
        }

    /** 最后心跳时间（持久化，用于周期心跳间隔判断） */
    private var lastHeartbeatTime: Long
        get() = prefs.getLong("anhao.spike.lastHeartbeat", 0)
        set(value) {
            prefs.edit().putLong("anhao.spike.lastHeartbeat", value).apply()
        }

    /** 防止并发心跳 */
    private val isSendingHeartbeat = java.util.concurrent.atomic.AtomicBoolean(false)

    // ── 可配置监测时段 ──

    /** 监测活动时段列表，仅在时段内才触发空闲告警 */
    var monitoringWindows: List<TimeWindow>
        get() {
            // 旧数据迁移
            migrateIfNeeded()
            val json = prefs.getString(KEY_WINDOWS, null) ?: return TimeWindow.DEFAULT
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { TimeWindow.fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                TimeWindow.DEFAULT
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it.toJson()) }
            prefs.edit().putString(KEY_WINDOWS, arr.toString()).apply()
            StillHereApp.instance.syncToCloud()
        }

    /** 旧数据迁移：睡眠时段 + 工作免打扰 → 监测时段 */
    private fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        if (!prefs.contains(KEY_OLD_WAKE) && !prefs.contains(KEY_OLD_SLEEP)) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        val wake = prefs.getInt(KEY_OLD_WAKE, 7)
        val sleep = prefs.getInt(KEY_OLD_SLEEP, 22)
        val workStart = prefs.getInt(KEY_OLD_WORK_START, 9)
        val workEnd = prefs.getInt(KEY_OLD_WORK_END, 18)

        val windows = mutableListOf<TimeWindow>()

        // 早晨：起床 → 工作开始
        if (wake < workStart) {
            windows.add(TimeWindow(wake, 0, workStart, 0, ""))
        }
        // 晚间：工作结束 → 睡觉
        if (workEnd < sleep) {
            windows.add(TimeWindow(workEnd, 0, sleep, 0, ""))
        }
        // 如果没有有效的监测时段，默认全天
        if (windows.isEmpty()) {
            windows.add(TimeWindow(0, 0, 23, 59, ""))
        }

        val arr = JSONArray()
        windows.forEach { arr.put(it.toJson()) }
        prefs.edit()
            .putString(KEY_WINDOWS, arr.toString())
            .putBoolean(KEY_MIGRATED, true)
            .apply()

        // 删除旧键
        prefs.edit()
            .remove(KEY_OLD_WAKE)
            .remove(KEY_OLD_SLEEP)
            .remove(KEY_OLD_WORK_START)
            .remove(KEY_OLD_WORK_END)
            .apply()
    }

    /** 检查当前时间是否在任一监测时段内 */
    fun isInMonitoringWindow(): Boolean {
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return monitoringWindows.any { it.contains(minutes) }
    }

    /** 空闲告警阈值（分钟），默认 30 */
    var idleAlertMinutes: Int
        get() = prefs.getInt("anhao.spike.idleAlertMinutes", DEFAULT_IDLE_MINUTES)
        set(value) {
            prefs.edit().putInt("anhao.spike.idleAlertMinutes", value.coerceIn(5, 240)).apply()
            StillHereApp.instance.syncToCloud()
        }

    /** 充电时是否忽略空闲告警，默认 false */
    var ignoreChargingForAlert: Boolean
        get() = prefs.getBoolean("anhao.spike.ignoreChargingAlert", false)
        set(value) {
            prefs.edit().putBoolean("anhao.spike.ignoreChargingAlert", value).apply()
            StillHereApp.instance.syncToCloud()
        }

    init {
        instance = this
    }

    // MARK: - 生命周期

    fun startAll() {
        if (_isRunning.value) return
        _isRunning.value = true

        val enabled = _enabledMonitors.value

        if (enabled.contains("Motion")) {
            motion = MotionMonitor(context, ::wake)
            motion?.start()
        }
        if (enabled.contains("Charging")) {
            charging = ChargingMonitor(context, ::wake)
            charging?.start()
        }
        if (enabled.contains("BGAppRefresh")) {
            BackgroundWorker.schedule(context)
        }

        // 定位相关：仅在权限已授予时启动
        if (enabled.contains("SLC") && hasLocationPermission()) {
            startLocationMonitors()
        }

        // 启动周期心跳
        startPeriodicHeartbeat()

        // 启动时立即上报前台状态（触发迁移、心跳、本地告警检查）
        reportForeground()
    }

    /** 权限就绪后启动定位监测 */
    fun startLocationMonitors() {
        if (!_isRunning.value) return
        val enabled = _enabledMonitors.value
        if (!enabled.contains("SLC")) return
        if (!hasLocationPermission()) return

        LocationMonitorService.startIfNeeded(context)
        slc = LocationMonitor(context, ::wake)
        slc?.start()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun stopAll() {
        _isRunning.value = false
        slc?.stop(); slc = null
        motion?.stop(); motion = null
        charging?.stop(); charging = null
        BackgroundWorker.cancel(context)
        LocationMonitorService.stop(context)
    }

    // MARK: - 周期心跳（约每小时一次）

    private fun startPeriodicHeartbeat() {
        scope.launch {
            while (isActive && _isRunning.value) {
                // 检查距离上次心跳是否已超过间隔
                val elapsed = System.currentTimeMillis() - lastHeartbeatTime
                if (elapsed >= HEARTBEAT_INTERVAL_MS) {
                    sendHeartbeat()
                }
                // 后台周期性检查本地告警（确保后台也能触发系统通知）
                checkLocalAlert()
                delay(60_000L) // 每分钟检查一次
            }
        }
    }

    /** 立即发送心跳（前台切入时调用） */
    fun sendHeartbeatNow() {
        scope.launch {
            sendHeartbeat()
        }
    }

    private suspend fun sendHeartbeat() {
        if (!isSendingHeartbeat.compareAndSet(false, true)) return
        try {
            val ok = Reporter.report(isCharging = isCharging)
            lastHeartbeatTime = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            if (ok) {
                _lastReportStatus.value = "✅ 心跳 $timeStr"
            } else {
                _lastReportStatus.value = "❌ 心跳失败 $timeStr"
            }
            android.util.Log.d("MonitorManager", "heartbeat result: $ok")
        } finally {
            isSendingHeartbeat.set(false)
        }
    }

    // MARK: - 前台切入

    fun reportForeground() {
        wake("Foreground", "app entered foreground")
        // 前台切入立即发送心跳（让关心人看到"刚刚活跃"）
        sendHeartbeatNow()
        // 检查本地告警
        checkLocalAlert()
    }

    // MARK: - 本地告警（基于监测时段）

    /**
     * 获取有效的最后活动时间（仅计算监测时段内的空闲）
     *
     * 如果 lastActivityTime 早于当前监测窗口的起始时间，则使用窗口起始时间，
     * 避免将非守护时段（如睡眠）的空闲计入告警。
     */
    private fun getEffectiveLastActivityTime(now: Long): Long {
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        val currentWindow = monitoringWindows.firstOrNull { it.contains(nowMinutes) }
            ?: return lastActivityTime

        // 计算最近一次该窗口的起始时间
        val windowStartCal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, currentWindow.startHour)
            set(Calendar.MINUTE, currentWindow.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 如果是跨日窗口（如 22:00–06:00），且当前在凌晨段，窗口起始应调整为昨天
        if (windowStartCal.timeInMillis > now) {
            windowStartCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        return maxOf(lastActivityTime, windowStartCal.timeInMillis)
    }

    /**
     * 检查是否需要生成本地告警
     *
     * 逻辑：当前时间在任一监测时段内，且空闲超过阈值 → 本机通知 + 5分钟延迟后上报
     */
    private fun checkLocalAlert() {
        // 不在任何监测时段内 → 跳过
        if (!isInMonitoringWindow()) return

        // 充电时忽略告警（用户在家充电，属于正常状态）
        if (ignoreChargingForAlert && isCharging) return

        val now = System.currentTimeMillis()

        // 仅计算当前监测时段内的空闲时长（非守护时段不计入）
        val effectiveLastActivity = getEffectiveLastActivityTime(now)
        val idleSeconds = (now - effectiveLastActivity) / 1000
        if (idleSeconds <= idleAlertMinutes * 60L) return

        // 如果已有待确认告警 → 跳过
        if (_pendingAlertMinutes.value != null) return

        // 避免同一时段重复告警（5 分钟内不重复）
        if (lastAlertTime > 0 && (now - lastAlertTime) < 5 * 60 * 1000) return

        lastAlertTime = now
        isAlerted = true
        val idleMinutes = (idleSeconds / 60).toInt()
        val event = "⚠️ 告警：已 ${idleMinutes} 分钟无活动"
        wake("Alert", event)

        // 显示系统通知
        showAlertNotification(idleMinutes)

        // 设置 5 分钟延迟，超时后才上报服务器
        _pendingAlertMinutes.value = idleMinutes
        alertTimerJob?.cancel()
        alertTimerJob = scope.launch {
            delay(5 * 60 * 1000L)
            firePendingAlert()
        }

        // 立即上报心跳，让关心人看到异常状态
        sendHeartbeatNow()
    }

    /** 显示告警本地通知 */
    private fun showAlertNotification(idleMinutes: Int) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, StillHereApp.GREETING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("晴好 · 活动超时提醒")
                .setContentText("已 ${idleMinutes} 分钟无活动，5分钟内未取消将通知关心人")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(3001, notification)
        } catch (_: Exception) {}
    }

    /** 超时后执行：上报到服务器 */
    private fun firePendingAlert() {
        val idleMinutes = _pendingAlertMinutes.value ?: return
        _pendingAlertMinutes.value = null
        alertTimerJob?.cancel()
        alertTimerJob = null
        scope.launch {
            val ok = Reporter.reportAlert(idleMinutes, isCharging)
            android.util.Log.d("MonitorManager", "firePendingAlert result: $ok")
        }
    }

    /** 用户取消待确认告警 */
    fun cancelPendingAlert() {
        if (_pendingAlertMinutes.value == null) return
        _pendingAlertMinutes.value = null
        alertTimerJob?.cancel()
        alertTimerJob = null
        lastAlertTime = 0  // 允许再次触发
        isAlerted = false
        scope.launch {
            val ok = Reporter.cancelAlert()
            android.util.Log.d("MonitorManager", "cancelPendingAlert result: $ok")
        }
        // 移除通知
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(3001)
    }

    // MARK: - 唤醒入口

    fun wake(source: String, event: String) {
        scope.launch {
            handleWakeInternal(source, event)
        }
    }

    /** 供 MonitorManager 直接调用（非 suspend） */
    fun handleWake(source: String, event: String) {
        scope.launch {
            handleWakeInternal(source, event)
        }
    }

    /**
     * 唤醒处理：记录活动时间 + 日志。
     * 不再每次事件都上报心跳，改为周期上报。
     * BGAppRefresh（后台定时任务）不算用户活动，不刷新 lastActivityTime，
     * 避免空闲计时器被周期性后台任务不断重置。
     */
    private suspend fun handleWakeInternal(source: String, event: String) {
        // 非后台定时任务、非告警事件才视为用户活动，更新空闲计时起点
        // BGAppRefresh：后台定时任务，不是用户真实活动
        // Alert：告警触发事件，不应重置空闲计时器也不应自我取消
        if (source != "BGAppRefresh" && source != "Alert") {
            lastActivityTime = System.currentTimeMillis()
        }
        val appState = "foreground"

        val entry = logger.record(source = source, event = event, appState = appState)
        android.util.Log.d("MonitorManager", "wake source=$source event=$event")

        // 如果之前处于告警状态 → 活动恢复，取消告警（包括本地定时器和通知）
        // 但不包括 Alert 自身触发的事件（避免告警刚触发就被自己取消）
        if (isAlerted && source != "Alert") {
            isAlerted = false
            lastAlertTime = 0
            // 取消本地 5 分钟倒计时，避免活动恢复后仍然上报
            _pendingAlertMinutes.value = null
            alertTimerJob?.cancel()
            alertTimerJob = null
            val ok = Reporter.cancelAlert()
            android.util.Log.d("MonitorManager", "cancelAlert result: $ok")
            // 移除通知
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.cancel(3001)
        }

        // 后台检测到活动时，若距上次心跳超过 2 分钟，发送心跳更新服务端状态
        val elapsed = System.currentTimeMillis() - lastHeartbeatTime
        if (elapsed >= 120_000L) {
            sendHeartbeat()
        }

        logger.markReported(entry.id)
    }

    fun notifyChargingState(charging: Boolean) {
        isCharging = charging
    }

    fun setEnabledMonitors(monitors: Set<String>) {
        _enabledMonitors.value = monitors
    }
}
