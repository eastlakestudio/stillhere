package com.eastlakestudio.stillhere.monitor

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlin.math.sqrt

/**
 * 运动感知监测器 —— 主方案：ActivityTransition API；兜底方案：加速度计
 *
 * 对应 iOS MotionActivityMonitor
 */
class MotionMonitor(
    private val context: Context,
    private val onWake: (String, String) -> Unit
) : Monitor {

    override val identifier = "Motion"

    private val transitionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ActivityTransitionResult.hasResult(intent)) {
                val result = ActivityTransitionResult.extractResult(intent) ?: return
                for (event in result.transitionEvents) {
                    val desc = describeActivity(event)
                    val transition = when (event.transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "entered"
                        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "exited"
                        else -> "unknown"
                    }
                    onWake(identifier, "activity: $desc ($transition)")
                }
            }
        }
    }

    private var isStarted = false
    private var registered = false
    private var usingFallback = false

    // ── 加速度计兜底 ──
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var fallbackStarted = false
    private var isMoving = false
    private var stillCount = 0
    private var movingCount = 0
    private val sampleWindow = ArrayDeque<Float>(40)

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            processAccelSample(event.values[0], event.values[1], event.values[2])
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun start() {
        if (isStarted) return

        // 检查身体活动权限（同时检查 Android 原生和 GMS 权限名）
        val hasAndroid = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        val hasGms = ContextCompat.checkSelfPermission(
            context, "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAndroid && !hasGms) {
            onWake(identifier, "activity monitoring skipped: ACTIVITY_RECOGNITION permission not granted")
            return
        }

        isStarted = true

        // Register broadcast receiver for activity transitions
        if (!registered) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                0
            }
            context.registerReceiver(
                transitionReceiver,
                IntentFilter("com.eastlakestudio.stillhere.ACTIVITY_TRANSITION"),
                flags
            )
            registered = true
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = PendingIntent.getBroadcast(
            context,
            2001,
            Intent("com.eastlakestudio.stillhere.ACTIVITY_TRANSITION"),
            flags
        )

        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
        )

        val request = ActivityTransitionRequest(transitions)
        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(request, intent)
            .addOnSuccessListener {
                onWake(identifier, "activity monitoring started")
            }
            .addOnFailureListener { e ->
                val msg = e.message ?: ""
                onWake(identifier, "activity monitoring failed: $msg")
                // API 不可用时降级为加速度计（国产设备 Play Services 受限）
                if (msg.contains("API_UNAVAILABLE") || msg.contains("API is not available")) {
                    onWake(identifier, "activity monitoring: falling back to accelerometer")
                    startAccelerometerFallback()
                    usingFallback = true
                }
            }
    }

    override fun stop() {
        isStarted = false
        if (registered) {
            try {
                context.unregisterReceiver(transitionReceiver)
            } catch (_: Exception) { }
            registered = false
        }
        // Remove transition updates
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
        }
        val intent = PendingIntent.getBroadcast(
            context,
            2001,
            Intent("com.eastlakestudio.stillhere.ACTIVITY_TRANSITION"),
            flags
        )
        intent?.let {
            ActivityRecognition.getClient(context).removeActivityTransitionUpdates(it)
        }
        // 停止加速度计兜底
        stopAccelerometerFallback()
    }

    // ──────────────── 加速度计兜底 ────────────────

    private fun startAccelerometerFallback() {
        if (fallbackStarted) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelSensor == null) {
            onWake(identifier, "accelerometer fallback: sensor not available")
            MonitorManager.instance?.setMotionAvailable(false)
            return
        }
        fallbackStarted = true
        sensorManager?.registerListener(
            accelListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL
        )
        MonitorManager.instance?.setMotionFallback(true)
        onWake(identifier, "accelerometer fallback started")
    }

    private fun stopAccelerometerFallback() {
        if (!fallbackStarted) return
        fallbackStarted = false
        usingFallback = false
        MonitorManager.instance?.setMotionFallback(false)
        try {
            sensorManager?.unregisterListener(accelListener)
        } catch (_: Exception) {}
        sensorManager = null
        accelSensor = null
        sampleWindow.clear()
        stillCount = 0
        movingCount = 0
    }

    /**
     * 处理加速度计采样数据
     *
     * 算法：计算加速度幅值方差 → 滑窗 + 滞回判定
     *   - 标准差 < 0.12 → 静止
     *   - 标准差 > 0.25 → 运动
     * 连续 5 次采样的状态一致才触发 onWake（防抖）
     */
    private fun processAccelSample(x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)

        sampleWindow.addLast(magnitude)
        if (sampleWindow.size > 40) sampleWindow.removeFirst()
        if (sampleWindow.size < 15) return // 采集不足

        // 计算标准差
        val n = sampleWindow.size
        val mean = sampleWindow.sum() / n
        val variance = sampleWindow.fold(0f) { acc, v -> acc + (v - mean) * (v - mean) } / n
        val std = sqrt(variance)

        when {
            std < 0.12f -> {
                stillCount++
                movingCount = 0
                if (stillCount >= 5 && isMoving) {
                    isMoving = false
                    onWake(identifier, "activity: stationary (accelerometer)")
                }
            }
            std > 0.25f -> {
                movingCount++
                stillCount = 0
                if (movingCount >= 5 && !isMoving) {
                    isMoving = true
                    onWake(identifier, "activity: moving (accelerometer)")
                }
            }
            else -> {
                stillCount = 0
                movingCount = 0
            }
        }
    }

    // ──────────────── Play Services 辅助 ────────────────

    private fun describeActivity(event: ActivityTransitionEvent): String {
        return when (event.activityType) {
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.IN_VEHICLE -> "automotive"
            DetectedActivity.ON_BICYCLE -> "cycling"
            DetectedActivity.STILL -> "stationary"
            DetectedActivity.UNKNOWN -> "unknown"
            else -> "other"
        }
    }
}
