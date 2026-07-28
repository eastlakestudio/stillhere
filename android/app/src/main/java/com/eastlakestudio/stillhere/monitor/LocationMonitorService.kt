package com.eastlakestudio.stillhere.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * 后台 Service —— 保持 LocationMonitor 生命周期
 *
 * 不显示常驻通知，不使用前台服务。
 * 告警轮询由 BackgroundWorker (WorkManager) 承担。
 */
class LocationMonitorService : Service() {

    companion object {
        fun startIfNeeded(context: Context) {
            val intent = Intent(context, LocationMonitorService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationMonitorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
