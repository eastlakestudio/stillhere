package com.eastlakestudio.stillhere

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.eastlakestudio.stillhere.ui.ContentScreen
import com.eastlakestudio.stillhere.ui.theme.StillHereTheme

class MainActivity : ComponentActivity() {

    private val permissions = mutableListOf<String>().apply {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private var permIndex = 0
    private var mainShown = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permIndex++
        requestNextPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (allGranted()) {
            StillHereApp.instance.monitorManager.startLocationMonitors()
        } else {
            requestNextPermission()
        }

        showMainScreen()
    }

    override fun onResume() {
        super.onResume()
        if (allGranted()) {
            StillHereApp.instance.monitorManager.startLocationMonitors()
        }
        // 用户已打开 App，清除所有本地通知与角标
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancelAll()
    }

    private fun requestNextPermission() {
        while (permIndex < permissions.size) {
            val perm = permissions[permIndex]
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
                permIndex++
                continue
            }
            permissionLauncher.launch(perm)
            return
        }
        if (allGranted()) {
            StillHereApp.instance.monitorManager.startLocationMonitors()
        }
    }

    private fun showMainScreen() {
        if (mainShown) return
        mainShown = true
        setContent {
            StillHereTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ContentScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun allGranted(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
