package com.eastlakestudio.stillhere.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.eastlakestudio.stillhere.data.PendingAlert
import com.eastlakestudio.stillhere.data.Reporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground Service —— 保持进程在后台存活，让 LocationMonitor 可以持续回调
 *
 * Android 没有 iOS SLC 那种"杀进程也能唤醒"机制，
 * 需要用 Foreground Service 保持进程存活。
 */
class LocationMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "stillhere_monitor"
        const val ALERT_CHANNEL_ID = "stillhere_alert"
        const val NOTIFICATION_ID = 1

        fun startIfNeeded(context: Context) {
            val intent = Intent(context, LocationMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationMonitorService::class.java)
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var pollJob: Job? = null
    private var lastAlertIds = mutableSetOf<Long>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startAlertPolling()
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName("com.eastlakestudio.stillhere.MainActivity")),
            flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安好 · 后台监测中")
            .setContentText("正在持续监测设备状态")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "后台监测",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "StillHere 后台监测服务通知"
        }
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "活动告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "被关心者的异常活动告警"
            enableVibration(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(alertChannel)
    }

    /**
     * 启动告警轮询协程（每 2 分钟拉取一次待处理告警，替代 FCM 推送）
     */
    private fun startAlertPolling() {
        pollJob = scope.launch {
            while (true) {
                try {
                    val alerts = Reporter.fetchPendingAlerts()
                    for (alert in alerts) {
                        if (alert.id !in lastAlertIds) {
                            lastAlertIds.add(alert.id)
                            showAlertNotification(alert)
                        }
                    }
                    // 定期清理旧 ID（保留最近 1000 条）
                    if (lastAlertIds.size > 1000) {
                        lastAlertIds = lastAlertIds.toList().takeLast(500).toMutableSet()
                    }
                } catch (_: Exception) {}
                delay(120_000L) // 2 分钟
            }
        }
    }

    private fun showAlertNotification(alert: PendingAlert) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alert.id.toInt(),
            Intent(this, Class.forName("com.eastlakestudio.stillhere.MainActivity")),
            flags
        )

        val title: String
        val body: String
        val icon: Int

        if (alert.isResolved) {
            title = "安好 · 活动已恢复 ✓"
            body = "「${alert.caredName}」已恢复活动"
            icon = android.R.drawable.ic_dialog_info
        } else when (alert.alertType) {
            "idle" -> {
                title = "安好 · 活动超时提醒"
                body = if (alert.isCharging) {
                    "「${alert.caredName}」已 ${alert.idleMinutes} 分钟无活动（充电中）"
                } else {
                    "「${alert.caredName}」已 ${alert.idleMinutes} 分钟无活动"
                }
                icon = android.R.drawable.ic_dialog_alert
            }
            "offline" -> {
                title = "安好 · 离线提醒"
                body = "「${alert.caredName}」可能已离线"
                icon = android.R.drawable.ic_dialog_alert
            }
            "online" -> {
                title = "安好 · 已上线"
                body = "「${alert.caredName}」已恢复在线"
                icon = android.R.drawable.ic_dialog_info
            }
            else -> {
                title = "安好"
                body = "「${alert.caredName}」有新消息"
                icon = android.R.drawable.ic_dialog_info
            }
        }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(alert.id.toInt(), notification)
    }
}
