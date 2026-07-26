package com.eastlakestudio.stillhere.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 充电状态监测器
 *
 * 对应 iOS ChargingMonitor
 */
class ChargingMonitor(
    private val context: Context,
    private val onWake: (String, String) -> Unit
) : Monitor {

    override val identifier = "Charging"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val levelPct = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "?"

            val desc = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "unplugged"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "unplugged"
                else -> "unknown"
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            MonitorManager.instance?.notifyChargingState(isCharging)

            onWake(identifier, "battery: $desc, level: $levelPct")
        }
    }

    private var isStarted = false

    override fun start() {
        if (isStarted) return
        isStarted = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        context.registerReceiver(receiver, filter, flags)
    }

    override fun stop() {
        isStarted = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) { }
    }
}
