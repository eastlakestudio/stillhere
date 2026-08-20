package com.eastlakestudio.stillhere.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eastlakestudio.stillhere.StillHereApp
import com.eastlakestudio.stillhere.data.CareRelation
import com.eastlakestudio.stillhere.data.CarerInfo
import com.eastlakestudio.stillhere.data.GreetingHistoryItem
import com.eastlakestudio.stillhere.data.PendingGreeting
import com.eastlakestudio.stillhere.data.Reporter
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                modifier = modifier,
                onConfigClick = { navController.navigate("config") },
                onTimeWindowClick = { navController.navigate("timeWindowConfig") }
            )
        }
        composable("config") {
            ConfigScreen(onBack = { navController.popBackStack() })
        }
        composable("timeWindowConfig") {
            TimeWindowConfigSheet(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onConfigClick: () -> Unit,
    onTimeWindowClick: () -> Unit
) {
    val app = StillHereApp.instance
    val caring by app.careStore.caring.collectAsState()
    val isRunning by app.monitorManager.isRunning.collectAsState()
    val caredByCount by app.careStore.caredByCountFlow.collectAsState()
    val deviceId = remember { Reporter.deviceId }
    val shortBindCode = remember { Reporter.careCode }

    var showAddCare by remember { mutableStateOf(false) }
    var showBindCode by remember { mutableStateOf(false) }
    var bindCodeInput by remember { mutableStateOf("") }
    var selectedRelation by remember { mutableStateOf<CareRelation?>(null) }

    // 关注我的人列表
    var carers by remember { mutableStateOf<List<CarerInfo>>(emptyList()) }
    var showCarers by remember { mutableStateOf(false) }

    // 问安相关
    var pendingGreetings by remember { mutableStateOf<List<PendingGreeting>>(emptyList()) }
    var greetingHistory by remember { mutableStateOf<List<GreetingHistoryItem>>(emptyList()) }
    var showGreetingHistory by remember { mutableStateOf(false) }
    var showGreetingReply by remember { mutableStateOf(false) }

    val qrBitmap = remember(shortBindCode) {
        generateQrCodeBitmap(shortBindCode, 400)
    }

    var showScanner by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 告警状态
    val pendingAlertMinutes by app.monitorManager.pendingAlertMinutes.collectAsState()

    // 监测时段摘要
    val timeWindowSummary = remember {
        val windows = app.monitorManager.monitoringWindows
        val totalMinutes = windows.sumOf { tw ->
            if (tw.endMinutes > tw.startMinutes) tw.endMinutes - tw.startMinutes
            else (24 * 60 - tw.startMinutes) + tw.endMinutes
        }
        val totalHours = totalMinutes / 60.0
        val hoursText = if (totalHours >= 1.0) "%.0f 小时".format(totalHours)
        else "${totalMinutes} 分钟"
        "${windows.size} 个时段 · 共 $hoursText"
    }

    // 相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showScanner = true
        }
    }

    LaunchedEffect(Unit) {
        app.monitorManager.startAll()
        app.careStore.refreshCaredStatus()
    }

    // 前台期间低频心跳（受距离上次心跳 gap 限制，防拖动服务器过载）
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60_000)
        while (true) {
            app.monitorManager.sendHeartbeatNow()
            kotlinx.coroutines.delay(60_000)
        }
    }

    // 加载关注我的人
    LaunchedEffect(caredByCount) {
        if (caredByCount > 0) {
            val result = Reporter.fetchCaredByMe(shortBindCode)
            carers = result
        }
    }

    // 轮询问安消息（低频兜底：Android 无 APNs 推送，问安记录存本地缓存，5 分钟一次防丢）
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60_000)
        while (true) {
            val gs = Reporter.fetchPendingGreetings(shortBindCode)
            if (gs.isNotEmpty() && !showGreetingReply) {
                // 仅在 App 前台时弹 BottomSheet，后台留给 BackgroundWorker 发系统通知
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    pendingGreetings = gs
                    showGreetingReply = true
                }
                // 标记为已展示，防止后台任务重复推送通知
                val prefs = context.getSharedPreferences("anhao.spike", android.content.Context.MODE_PRIVATE)
                val seenIds = prefs.getString("anhao.spike.notifiedGreetingIds", "")?.split(",")
                    ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
                val allIds = (seenIds + gs.map { it.id }).toSortedSet()
                prefs.edit().putString("anhao.spike.notifiedGreetingIds", allIds.joinToString(",")).apply()
            }
            app.careStore.refreshCaredStatus()
            kotlinx.coroutines.delay(5 * 60 * 1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "晴好",
                            fontWeight = FontWeight.Light,
                            fontSize = 22.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 添加关心
                FilledTonalButton(
                    onClick = { showAddCare = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("添加关心", style = MaterialTheme.typography.labelLarge)
                }

                // 报送平安 / 守护中
                val bgColor = if (isRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerLow

                val fgColor = if (isRunning)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant

                val accentColor = if (isRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isRunning) Brush.linearGradient(
                                colors = listOf(
                                    bgColor.copy(alpha = 0.7f),
                                    bgColor
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ) else Brush.linearGradient(
                                colors = listOf(bgColor, bgColor),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        )
                        .clickable {
                            if (isRunning) {
                                app.monitorManager.stopAll()
                            } else {
                                app.monitorManager.startAll()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRunning) accentColor
                                    else accentColor.copy(alpha = 0.3f)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                if (isRunning) "守护中…" else "报送平安",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = fgColor
                            )
                            if (isRunning) {
                                Text(
                                    "持续守护活动状态并定时上报",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fgColor.copy(alpha = 0.6f)
                                )
                            } else {
                                Text(
                                    "点击开始守护",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fgColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // 守护时间段配置
                Card(
                    onClick = onTimeWindowClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "守护时间段配置",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                timeWindowSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }

                // 系统配置
                Card(
                    onClick = onConfigClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "系统配置",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 待确认告警横幅 ──
            val alertMinutes = pendingAlertMinutes
            if (alertMinutes != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "活动超时提醒",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "已 ${alertMinutes} 分钟无活动，若不取消将通知关心人",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { app.monitorManager.cancelPendingAlert() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("取消", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ── 问安记录入口 ──
            item {
                OutlinedButton(
                    onClick = {
                        // 纯本地缓存读取，不查服务器
                        greetingHistory = Reporter.cachedGreetingHistory()
                        showGreetingHistory = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("问安记录", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── 被关心卡片 ──
            item {
                CaredByCard(
                    count = caredByCount,
                    days = app.careStore.totalCaredDays(caredByCount),
                    onShowBindCode = { showBindCode = true },
                    onToggleCarers = {
                        showCarers = !showCarers
                    }
                )
            }

            // ── 关注我的人列表（可展开） ──
            if (showCarers && carers.isNotEmpty()) {
                item {
                    CarersListSection(
                        carers = carers,
                        caringCodes = caring.map { it.bindCode }.toSet(),
                        caringNameMap = caring.associate { it.bindCode to it.name },
                        onAdd = { code ->
                            bindCodeInput = code
                            showCarers = false
                            showAddCare = true
                        }
                    )
                }
            }

            // ── 我关心 ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💚 我关心",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (caring.isNotEmpty()) {
                        Text(
                            "${caring.size} 位 · ${app.careStore.totalCaringDays} 天",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 关心列表（最少 3 个卡片位）
            val cardCount = maxOf(caring.size, 3)
            for (i in 0 until cardCount) {
                if (i < caring.size) {
                    val rel = caring[i]
                    item {
                        CaringCard(relation = rel, onClick = { selectedRelation = rel },
                            onGreeting = { code ->
                                scope.launch {
                                    val id = Reporter.sendGreeting(code)
                                    if (id != null) {
                                        android.widget.Toast.makeText(
                                            StillHereApp.instance,
                                            "已向「${rel.name}」发送问安",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                } else {
                    item {
                        EmptyCaringPlaceholder()
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showBindCode) {
        BindCodeSheet(
            bindCode = shortBindCode,
            qrBitmap = qrBitmap,
            onDismiss = { showBindCode = false }
        )
    }

    if (showAddCare) {
        AddCareSheet(
            bindCode = bindCodeInput,
            onBindCodeChange = { bindCodeInput = it },
            onDismiss = {
                bindCodeInput = ""
                showAddCare = false
            },
            onAdd = { name, code ->
                app.careStore.addCaring(name, code)
                app.careStore.refreshCaredStatus()
                bindCodeInput = ""
                showAddCare = false
            },
            onScan = {
                showAddCare = false
                // 检查相机权限
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    showScanner = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )
    }

    selectedRelation?.let { rel ->
        CareDetailSheet(
            relation = rel,
            onDismiss = { selectedRelation = null },
            onUpdateName = { newName ->
                app.careStore.updateCaringName(rel, newName)
                selectedRelation = null
            },
            onRemove = {
                app.careStore.removeCaring(rel)
                selectedRelation = null
            },
            onGreeting = { code ->
                scope.launch {
                    val id = Reporter.sendGreeting(code)
                    if (id != null) {
                        android.widget.Toast.makeText(
                            StillHereApp.instance,
                            "已向「${rel.name}」发送问安",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        selectedRelation = null
                    }
                }
            }
        )
    }

    // 问安回复弹窗
    if (showGreetingReply && pendingGreetings.isNotEmpty()) {
        val caringMap = remember(caring) { caring.associate { it.bindCode to it.name } }
        GreetingReplySheet(
            greetings = pendingGreetings,
            caringMap = caringMap,
            onReply = { greetingId, reply ->
                scope.launch {
                    val ok = Reporter.replyGreeting(greetingId, reply)
                    if (ok) {
                        pendingGreetings = emptyList()
                        showGreetingReply = false
                        android.widget.Toast.makeText(
                            StillHereApp.instance,
                            "已回复",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = {
                showGreetingReply = false
            }
        )
    }

    // 问安记录弹窗
    if (showGreetingHistory) {
        GreetingHistorySheet(
            history = greetingHistory,
            onDismiss = { showGreetingHistory = false }
        )
    }

    // 扫码弹窗
    if (showScanner) {
        QrScannerSheet(
            onScanned = { code ->
                val scanned = code.trim().uppercase().take(6)
                if (scanned.length == 6) bindCodeInput = scanned
                showScanner = false
                showAddCare = true
            },
            onDismiss = { showScanner = false }
        )
    }
}

fun generateQrCodeBitmap(content: String, size: Int): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
