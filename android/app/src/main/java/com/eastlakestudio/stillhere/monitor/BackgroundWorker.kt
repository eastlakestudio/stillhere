package com.eastlakestudio.stillhere.monitor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.os.Build
import com.eastlakestudio.stillhere.MainActivity
import com.eastlakestudio.stillhere.StillHereApp
import com.eastlakestudio.stillhere.data.PendingAlert
import com.eastlakestudio.stillhere.data.Reporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager 兜底后台心跳 + 问安消息轮询
 *
 * 对应 iOS BackgroundRefreshMonitor (BGAppRefreshTask)
 */
class BackgroundWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "stillhere_background_refresh"

        /** 问安通知去重：已通知过的 greeting ID 集合 */
        private const val PREF_NOTIFIED_IDS = "anhao.spike.notifiedGreetingIds"

        /** 告警通知去重：已通知过的 alert ID 集合 */
        private const val PREF_NOTIFIED_ALERT_IDS = "anhao.spike.notifiedAlertIds"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackgroundWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** 清除已通知记录（用户回复问安后调用，允许下次再通知） */
        fun clearNotifiedGreetings(context: Context) {
            context.getSharedPreferences("anhao.spike", Context.MODE_PRIVATE)
                .edit().remove(PREF_NOTIFIED_IDS).apply()
        }
    }

    override suspend fun doWork(): Result {
        Log.d("BackgroundWorker", "periodic work fired")

        // 1. 维持后台心跳（不更新 lastActivityTime，避免重置空闲计时器）
        MonitorManager.instance?.handleWake("BGAppRefresh", "periodic work fired")

        // 2. 轮询问安消息
        checkPendingGreetings()

        // 3. 轮询待处理告警
        checkPendingAlerts()

        return Result.success()
    }

    private suspend fun checkPendingGreetings() = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("anhao.spike", Context.MODE_PRIVATE)
            val careCode = Reporter.careCode
            val greetings = Reporter.fetchPendingGreetings(careCode)

            if (greetings.isEmpty()) return@withContext

            // 去重：只通知之前未见过的新问安
            val seenIds = prefs.getString(PREF_NOTIFIED_IDS, "")?.split(",")
                ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

            val newGreetings = greetings.filter { it.id !in seenIds }
            if (newGreetings.isEmpty()) return@withContext

            // 更新已通知 ID 集合
            val allIds = (seenIds + greetings.map { it.id }).toSortedSet()
            prefs.edit().putString(PREF_NOTIFIED_IDS, allIds.joinToString(",")).apply()

            // 发送系统通知
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(
                applicationContext,
                StillHereApp.GREETING_CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("问安消息")
                .setContentText(
                    if (newGreetings.size == 1) "收到一条新的问安，点击查看"
                    else "收到 ${newGreetings.size} 条新的问安，点击查看"
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            NotificationManagerCompat.from(applicationContext).notify(2001, notification)

            Log.d("BackgroundWorker", "sent greeting notification for ${newGreetings.size} new greetings")
        } catch (e: Exception) {
            Log.e("BackgroundWorker", "checkPendingGreetings failed: ${e.message}")
        }
    }

    private suspend fun checkPendingAlerts() = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("anhao.spike", Context.MODE_PRIVATE)
            val alerts = Reporter.fetchPendingAlerts()

            if (alerts.isEmpty()) return@withContext

            val seenIds = prefs.getString(PREF_NOTIFIED_ALERT_IDS, "")?.split(",")
                ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

            val newAlerts = alerts.filter { it.id !in seenIds }
            if (newAlerts.isEmpty()) return@withContext

            val allIds = (seenIds + alerts.map { it.id }).toSortedSet()
            // 保留最近 1000 条，避免无限增长
            val trimmed = if (allIds.size > 500) allIds.toList().takeLast(500).toSortedSet() else allIds
            prefs.edit().putString(PREF_NOTIFIED_ALERT_IDS, trimmed.joinToString(",")).apply()

            for (alert in newAlerts) {
                showAlertNotification(alert)
            }

            Log.d("BackgroundWorker", "sent ${newAlerts.size} alert notifications")
        } catch (e: Exception) {
            Log.e("BackgroundWorker", "checkPendingAlerts failed: ${e.message}")
        }
    }

    private fun showAlertNotification(alert: PendingAlert) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            alert.id.toInt(),
            Intent(applicationContext, MainActivity::class.java),
            flags
        )

        val title: String
        val body: String
        val icon: Int

        if (alert.isResolved) {
            title = "晴好 · 活动已恢复 ✓"
            body = "\u300C${alert.caredName}\u300D已恢复活动"
            icon = android.R.drawable.ic_dialog_info
        } else when (alert.alertType) {
            "idle" -> {
                title = "晴好 · 活动超时提醒"
                body = if (alert.isCharging) {
                    "\u300C${alert.caredName}\u300D已 ${alert.idleMinutes} 分钟无活动（充电中）"
                } else {
                    "\u300C${alert.caredName}\u300D已 ${alert.idleMinutes} 分钟无活动"
                }
                icon = android.R.drawable.ic_dialog_alert
            }
            "offline" -> {
                title = "晴好 · 离线提醒"
                body = "\u300C${alert.caredName}\u300D可能已离线"
                icon = android.R.drawable.ic_dialog_alert
            }
            "stale" -> {
                title = "晴好 · 心跳异常"
                body = "\u300C${alert.caredName}\u300D已 ${alert.idleMinutes} 分钟未上报状态"
                icon = android.R.drawable.ic_dialog_alert
            }
            "online" -> {
                title = "晴好 · 已上线"
                body = "\u300C${alert.caredName}\u300D已恢复在线"
                icon = android.R.drawable.ic_dialog_info
            }
            else -> {
                title = "晴好"
                body = "\u300C${alert.caredName}\u300D有新消息"
                icon = android.R.drawable.ic_dialog_info
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, StillHereApp.ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(alert.id.toInt(), notification)
    }
}
