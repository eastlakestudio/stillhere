package com.eastlakestudio.stillhere.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启 —— 设备重启后自动启动监测
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MonitorManager.instance?.startAll()
        }
    }
}
