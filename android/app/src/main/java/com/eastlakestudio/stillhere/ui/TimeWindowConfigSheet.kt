package com.eastlakestudio.stillhere.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eastlakestudio.stillhere.StillHereApp
import com.eastlakestudio.stillhere.monitor.TimeWindow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeWindowConfigSheet(onBack: () -> Unit) {
    val app = StillHereApp.instance
    val windows = remember { mutableStateOf(app.monitorManager.monitoringWindows) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val idleMin = app.monitorManager.idleAlertMinutes
    var idleSlider by remember(idleMin) { mutableStateOf(idleMin.toFloat()) }

    // 编辑 / 新增弹窗
    if (editingIndex != null || showAddDialog) {
        val isEdit = editingIndex != null
        val current = if (isEdit) windows.value.getOrNull(editingIndex!!) else TimeWindow(9, 0, 18, 0, "")
        TimeWindowEditDialog(
            initial = current,
            title = if (isEdit) "编辑守护时段" else "添加守护时段",
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("守护时间段配置") },
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
            // 说明文字
            item {
                Text(
                    "仅在配置的时段内检测空闲告警，时段外不打扰",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ── 时段列表 ──
            item {
                val accentColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.secondary,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "守护时段",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

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
                            Text("添加守护时段", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ── 静置告警阈值 ──
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
                            "静置告警阈值",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                            steps = 22,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── 充电忽略 ──
            item {
                val ignoreCharging = app.monitorManager.ignoreChargingForAlert
                var ignoreChargingChecked by remember(ignoreCharging) { mutableStateOf(ignoreCharging) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ignoreChargingChecked = !ignoreChargingChecked
                                app.monitorManager.ignoreChargingForAlert = ignoreChargingChecked
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "充电时忽略空闲告警",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "充电时视为用户在家，不触发告警",
                                style = MaterialTheme.typography.bodySmall,
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
