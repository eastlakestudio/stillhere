package com.eastlakestudio.stillhere.monitor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * 位置监测器 —— Android 没有 SLC，用持续定位 + Foreground Service 模拟
 *
 * 对应 iOS LocationSLCMonitor
 */
class LocationMonitor(
    private val context: Context,
    private val onWake: (String, String) -> Unit
) : Monitor {

    override val identifier = "SLC"

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5 * 60 * 1000L // 5 分钟间隔
    ).setMinUpdateIntervalMillis(3 * 60 * 1000L) // 最快 3 分钟
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            onWake(identifier, "location update (${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)})")
        }
    }

    private var isStarted = false

    override fun start() {
        if (isStarted) return

        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (hasFine != PackageManager.PERMISSION_GRANTED && hasCoarse != PackageManager.PERMISSION_GRANTED) {
            onWake(identifier, "location permission not granted — requesting")
            return
        }

        isStarted = true
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, null)
        onWake(identifier, "location monitoring started")
    }

    override fun stop() {
        isStarted = false
        fusedClient.removeLocationUpdates(locationCallback)
    }
}
