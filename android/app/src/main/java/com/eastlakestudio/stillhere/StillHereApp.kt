package com.eastlakestudio.stillhere

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.eastlakestudio.stillhere.data.CareStore
import com.eastlakestudio.stillhere.data.Logger
import com.eastlakestudio.stillhere.data.Reporter
import com.eastlakestudio.stillhere.monitor.MonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application — 初始化全局单例
 *
 * 对应 iOS @main App
 */
class StillHereApp : Application() {

    lateinit var logger: Logger
    lateinit var careStore: CareStore
    lateinit var monitorManager: MonitorManager

    override fun onCreate() {
        super.onCreate()
        instance = this

        Reporter.init(this)
        logger = Logger(this)
        careStore = CareStore(this)
        monitorManager = MonitorManager(this, logger)

        // 问安通知渠道
        val greetingChannel = NotificationChannel(
            GREETING_CHANNEL_ID,
            "问安消息",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "收到关心人的问安消息"
            enableVibration(true)
        }
        // 告警通知渠道
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "活动告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "被关心者的异常活动告警"
            enableVibration(true)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(greetingChannel)
        nm.createNotificationChannel(alertChannel)

        restoreFromCloud()
    }

    private val syncScope = CoroutineScope(Dispatchers.IO)

    private fun restoreFromCloud() {
        syncScope.launch {
            try {
                val config = Reporter.loadConfig() ?: return@launch

                // 恢复守护时段
                val windows = config["monitoringWindows"] as? List<*>
                if (windows != null && windows.isNotEmpty()) {
                    val restored = windows.mapNotNull { w ->
                        val m = w as? Map<*, *> ?: return@mapNotNull null
                        com.eastlakestudio.stillhere.monitor.TimeWindow(
                            (m["startHour"] as? Double)?.toInt() ?: 9,
                            (m["startMinute"] as? Double)?.toInt() ?: 0,
                            (m["endHour"] as? Double)?.toInt() ?: 18,
                            (m["endMinute"] as? Double)?.toInt() ?: 0,
                            m["label"] as? String ?: ""
                        )
                    }
                    if (restored.isNotEmpty()) monitorManager.monitoringWindows = restored
                }

                // 恢复告警阈值
                (config["idleAlertMinutes"] as? Double)?.toInt()?.let {
                    monitorManager.idleAlertMinutes = it
                }

                // 恢复充电忽略
                (config["ignoreChargingForAlert"] as? Boolean)?.let {
                    monitorManager.ignoreChargingForAlert = it
                }

                // 恢复昵称
                val nicknames = config["nicknames"] as? Map<*, *>
                if (nicknames != null) {
                    nicknames.forEach { (code, name) ->
                        careStore.updateCaringNameByCode(code.toString(), name.toString())
                    }
                }

                android.util.Log.d("StillHereApp", "config restored from cloud")
            } catch (e: Exception) {
                android.util.Log.e("StillHereApp", "restoreFromCloud failed: ${e.message}")
            }
            // 启动时全量上传一次（含时区），确保服务端拿到最新守护配置用于裁决
            syncToCloud()
        }
    }

    fun syncToCloud() {
        syncScope.launch {
            try {
                val config = mapOf(
                    "monitoringWindows" to monitorManager.monitoringWindows.map {
                        mapOf(
                            "startHour" to it.startHour,
                            "startMinute" to it.startMinute,
                            "endHour" to it.endHour,
                            "endMinute" to it.endMinute,
                            "label" to it.label
                        )
                    },
                    "idleAlertMinutes" to monitorManager.idleAlertMinutes,
                    "ignoreChargingForAlert" to monitorManager.ignoreChargingForAlert,
                    "timezoneOffsetMinutes" to (java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000),
                    "nicknames" to careStore.caring.value.associate { it.bindCode to it.name }
                )
                Reporter.saveConfig(config)
            } catch (e: Exception) {
                android.util.Log.e("StillHereApp", "syncToCloud failed: ${e.message}")
            }
        }
    }

    companion object {
        lateinit var instance: StillHereApp
            private set

        const val GREETING_CHANNEL_ID = "greeting_channel"
        const val ALERT_CHANNEL_ID = "stillhere_alert"
    }
}