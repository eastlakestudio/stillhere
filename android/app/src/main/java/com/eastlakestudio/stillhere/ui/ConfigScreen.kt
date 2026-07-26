package com.eastlakestudio.stillhere.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.eastlakestudio.stillhere.StillHereApp
import com.eastlakestudio.stillhere.data.LogEntry
import com.eastlakestudio.stillhere.monitor.TimeWindow
import kotlin.math.roundToInt

private val sources = listOf("SLC", "Motion", "BGAppRefresh", "Charging", "Foreground", "Alert")

private fun sourceLabel(source: String): String = when (source) {
    "SLC" -> "位置唤醒"
    "Motion" -> "运动感知"
    "BGAppRefresh" -> "后台刷新"
    "Charging" -> "充电状态"
    "Foreground" -> "前台切入"
    "Alert" -> "告警"
    else -> source
}

private fun appStateLabel(appState: String): String = when (appState) {
    "foreground" -> "前台"
    "background" -> "后台"
    "inactive" -> "非活跃"
    else -> appState
}

// 柔和的来源色 — 基于主题色系
@Composable
private fun sourceColor(index: Int) = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
    MaterialTheme.colorScheme.error,
)[index % 6]

// Google Play Services 在某些 ROM 上检查的权限名
private const val GMS_ACTIVITY_RECOGNITION = "com.google.android.gms.permission.ACTIVITY_RECOGNITION"

@Composable
private fun hasActivityRecognitionPermission(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
        context,
        GMS_ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onBack: () -> Unit) {
    val app = StillHereApp.instance
    val lastReportStatus by app.monitorManager.lastReportStatus.collectAsState()
    val enabledMonitors by app.monitorManager.enabledMonitors.collectAsState()
    val motionAvailable by app.monitorManager.motionAvailable.collectAsState()
    val isMotionFallback by app.monitorManager.isMotionFallback.collectAsState()
    val entries by app.logger.entries.collectAsState()
    val stats by app.logger.stats.collectAsState()

    var filterSource by remember { mutableStateOf<String?>(null) }
    var logExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 是否需要引导用户到设置页手动开启
    var needOpenSettings by remember { mutableStateOf(false) }

    // 身体活动权限请求
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 如果弹窗被 ROM 拦截跳到了设置页，下次需要直接用设置
        if (!granted && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                context as android.app.Activity,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(
                context, GMS_ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED) {
            needOpenSettings = true
        }
    }

    // 打开应用设置页（仅当权限被永久拒绝时）
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { needOpenSettings = false }

    val filteredEntries = if (filterSource != null) {
        entries.filter { it.source == filterSource }
    } else {
        entries
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("系统配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // MARK: - 上报状态
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CellTower,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        lastReportStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // MARK: - 监测器开关
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "监测器开关",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        sources.forEachIndexed { idx, source ->
                            val color = sourceColor(idx)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(color.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        sourceLabel(source),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Switch(
                                    checked = enabledMonitors.contains(source),
                                    onCheckedChange = { checked ->
                                        val newSet = enabledMonitors.toMutableSet()
                                        if (checked) newSet.add(source) else newSet.remove(source)
                                        app.monitorManager.setEnabledMonitors(newSet)
                                    }
                                )
                            }
                        }

                        // 运动感知状态提示
                        if (enabledMonitors.contains("Motion")) {
                            if (!hasActivityRecognitionPermission()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                if (needOpenSettings) {
                                    // 权限被永久拒绝 → 引导到应用设置页
                                    PermissionSettingsBar(
                                        text = "请在设置中开启「身体活动」权限",
                                        onClick = {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            settingsLauncher.launch(intent)
                                        }
                                    )
                                } else {
                                    // 首次请求 → 标准权限弹窗
                                    PermissionSettingsBar(
                                        text = "缺少「身体活动」权限，运动感知无法工作。点此授权",
                                        onClick = {
                                            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        }
                                    )
                                }
                            } else if (isMotionFallback) {
                                // 加速度计兜底模式 → 成功提示
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "运动感知（加速度计模式）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            } else if (!motionAvailable) {
                                // 权限已有但 API 不可用 → 仅提示
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "此设备不支持运动感知（Play Services 受限）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MARK: - 监测活动时间
            item {
                val windows = remember { mutableStateOf(app.monitorManager.monitoringWindows) }
                var editingIndex by remember { mutableStateOf<Int?>(null) }
                var showAddDialog by remember { mutableStateOf(false) }

                // 编辑 / 新增弹窗
                if (editingIndex != null || showAddDialog) {
                    val isEdit = editingIndex != null
                    val current = if (isEdit) windows.value.getOrNull(editingIndex!!) else TimeWindow(9, 0, 18, 0, "")
                    TimeWindowEditDialog(
                        initial = current,
                        title = if (isEdit) "编辑监测时段" else "添加监测时段",
                        onDismiss = {
                            editingIndex = null
                            showAddDialog = false
                        },
                        onConfirm = { tw ->
                            val list = windows.value.toMutableList()
                            if (isEdit && editingIndex != null) {
                                list[editingIndex!!] = tw
                            } else {
                                list.add(tw)
                            }
                            windows.value = list
                            app.monitorManager.monitoringWindows = list
                            editingIndex = null
                            showAddDialog = false
                        }
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "监测活动时间",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "仅在配置的时段内检测空闲告警，时段外不打扰",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── 时段列表 ──
                        val accentColors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.secondary,
                        )

                        windows.value.forEachIndexed { idx, tw ->
                            val accent = accentColors[idx % accentColors.size]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { editingIndex = idx }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左侧色条
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accent.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${tw.startHour.toString().padStart(2, '0')}:${tw.startMinute.toString().padStart(2, '0')}  –  ${tw.endHour.toString().padStart(2, '0')}:${tw.endMinute.toString().padStart(2, '0')}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (tw.label.isNotEmpty()) {
                                        Text(
                                            tw.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        val list = windows.value.toMutableList()
                                        list.removeAt(idx)
                                        windows.value = list
                                        app.monitorManager.monitoringWindows = list
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // 添加按钮
                        TextButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加监测时段", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ── 空闲告警阈值 ──
                        Text(
                            "空闲告警阈值",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val idleMin = app.monitorManager.idleAlertMinutes
                        var idleSlider by remember(idleMin) { mutableStateOf(idleMin.toFloat()) }

                        Text(
                            "${idleSlider.roundToInt()} 分钟无活动则告警",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = idleSlider,
                            onValueChange = { idleSlider = it },
                            onValueChangeFinished = {
                                app.monitorManager.idleAlertMinutes = idleSlider.roundToInt()
                            },
                            valueRange = 5f..120f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── 充电忽略 ──
                        val ignoreCharging = app.monitorManager.ignoreChargingForAlert
                        var ignoreChargingChecked by remember(ignoreCharging) { mutableStateOf(ignoreCharging) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    ignoreChargingChecked = !ignoreChargingChecked
                                    app.monitorManager.ignoreChargingForAlert = ignoreChargingChecked
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "充电时忽略空闲告警",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "充电时视为用户在家，不触发告警",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = ignoreChargingChecked,
                                onCheckedChange = {
                                    ignoreChargingChecked = it
                                    app.monitorManager.ignoreChargingForAlert = it
                                }
                            )
                        }
                    }
                }
            }

            // MARK: - 唤醒统计
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "唤醒统计",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        sources.forEachIndexed { idx, source ->
                            val color = sourceColor(idx)
                            val isFiltered = filterSource == source

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isFiltered) Modifier.background(
                                            color.copy(alpha = 0.08f)
                                        ) else Modifier
                                    )
                                    .clickable {
                                        filterSource = if (isFiltered) null else source
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        sourceLabel(source),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isFiltered) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isFiltered)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${stats[source] ?: 0}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isFiltered) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.FilterListOff,
                                            contentDescription = "取消筛选",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MARK: - 唤醒日志（可折叠）
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 头部：点击折叠/展开
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { logExpanded = !logExpanded }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (filterSource != null) "${sourceLabel(filterSource!!)} 日志"
                                    else "唤醒日志",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (filterSource != null)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (filteredEntries.isEmpty()) "暂无记录"
                                    else "${filteredEntries.size} 条记录${if (logExpanded) "" else "，点击展开"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (filterSource != null) {
                                    TextButton(onClick = { filterSource = null }) {
                                        Text("清除", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Icon(
                                    if (logExpanded) Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (logExpanded) "收起" else "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 可折叠内容
                        AnimatedVisibility(
                            visible = logExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))

                                if (filteredEntries.isEmpty()) {
                                    Text(
                                        if (filterSource == null) "暂无唤醒记录" else "暂无该监测器的记录",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    filteredEntries.forEach { entry ->
                                        LogRow(entry)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun LogRow(entry: LogEntry) {
    val color = sourceColor(sources.indexOf(entry.source).coerceAtLeast(0))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Source badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                sourceLabel(entry.source),
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(entry.event, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    appStateLabel(entry.appState),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.Filled.Circle,
                    contentDescription = null,
                    tint = if (entry.reportedRemote)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeWindowEditDialog(
    initial: TimeWindow?,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (TimeWindow) -> Unit
) {
    // 从 initial 反算持续时长（分钟）
    val initialDurationMinutes = if (initial != null) {
        val start = initial.startMinutes
        val end = initial.endMinutes
        if (end > start) end - start else (24 * 60 - start) + end
    } else {
        9 * 60 // 默认 9 小时
    }

    var startHour by remember(initial) { mutableStateOf((initial?.startHour ?: 9).toFloat()) }
    var startMinute by remember(initial) { mutableStateOf((initial?.startMinute ?: 0).toFloat()) }
    var durationHours by remember(initial) { mutableStateOf((initialDurationMinutes / 60f)) }
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }

    // 计算结束时间
    val totalStart = startHour.roundToInt() * 60 + startMinute.roundToInt()
    val totalEnd = (totalStart + (durationHours * 60).roundToInt()) % (24 * 60)
    val computedEndHour = totalEnd / 60
    val computedEndMinute = totalEnd % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 开始时间
                Text(
                    "开始时间  ${startHour.roundToInt().toString().padStart(2, '0')}:${startMinute.roundToInt().toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("时", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(20.dp))
                    Slider(
                        value = startHour,
                        onValueChange = { startHour = it },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(20.dp))
                    Slider(
                        value = startMinute,
                        onValueChange = { startMinute = it },
                        valueRange = 0f..59f,
                        steps = 58,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 持续时长
                val durDisplay = if (durationHours >= 1f) {
                    "%.1f 小时".format(durationHours)
                } else {
                    "%d 分钟".format((durationHours * 60).roundToInt())
                }
                Text(
                    "持续时长  $durDisplay",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = durationHours,
                    onValueChange = { durationHours = (it * 2).roundToInt() / 2f },
                    valueRange = 0.5f..24f,
                    steps = 46,
                    modifier = Modifier.fillMaxWidth()
                )

                // 结束时间提示
                Text(
                    "预计结束  ${computedEndHour.toString().padStart(2, '0')}:${computedEndMinute.toString().padStart(2, '0')}" +
                        if (totalEnd <= totalStart) "（次日）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 标签
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("标签（可选）") },
                    placeholder = { Text("如：晨间、工作时间") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    TimeWindow(
                        startHour = startHour.roundToInt().coerceIn(0, 23),
                        startMinute = startMinute.roundToInt().coerceIn(0, 59),
                        endHour = computedEndHour,
                        endMinute = computedEndMinute,
                        label = label.trim()
                    )
                )
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 权限提示条组件
 */
@Composable
private fun PermissionSettingsBar(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
