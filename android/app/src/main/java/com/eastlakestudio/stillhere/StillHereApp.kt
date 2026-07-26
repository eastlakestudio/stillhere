package com.eastlakestudio.stillhere

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.eastlakestudio.stillhere.data.CareStore
import com.eastlakestudio.stillhere.data.Logger
import com.eastlakestudio.stillhere.data.Reporter
import com.eastlakestudio.stillhere.monitor.MonitorManager

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
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(greetingChannel)
    }

    companion object {
        lateinit var instance: StillHereApp
            private set

        const val GREETING_CHANNEL_ID = "greeting_channel"
    }
}