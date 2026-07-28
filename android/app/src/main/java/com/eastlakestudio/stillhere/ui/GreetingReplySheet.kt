package com.eastlakestudio.stillhere.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eastlakestudio.stillhere.data.PendingGreeting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 问安回复底部弹窗
 * 显示收到的问安消息，支持快捷"晴好"回复或自定义文本
 * 同一发送人只保留最后一条问安；若发送人是关心人则有昵称
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GreetingReplySheet(
    greetings: List<PendingGreeting>,
    caringMap: Map<String, String> = emptyMap(),  // careCode → nickname
    onReply: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var customReply by remember { mutableStateOf("") }

    // 去重：同一 fromCareCode 只保留 id 最大（最新）的那条
    val deduped = remember(greetings) {
        greetings.groupBy { it.fromCareCode }
            .mapValues { (_, list) -> list.maxBy { it.id } }
            .values.toList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "问安消息",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            deduped.forEach { g ->
                val label = caringMap[g.fromCareCode] ?: g.fromCareCode
                val actionText = if (g.isReply) "答复了你" else "向你问安"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            actionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (g.isReply) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (g.createdAt > 0) {
                        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        Text(
                            sdf.format(Date(g.createdAt * 1000)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    if (g.message.isNotEmpty()) {
                        Text(
                            "\"${g.message}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 快捷回复：晴好
            OutlinedButton(
                onClick = {
                    deduped.forEach { g -> onReply(g.id, "晴好") }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("晴好", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 自定义回复
            OutlinedTextField(
                value = customReply,
                onValueChange = { customReply = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义回复") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val text = customReply.trim()
                    if (text.isNotEmpty()) {
                        deduped.forEach { g -> onReply(g.id, text) }
                        customReply = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = customReply.trim().isNotEmpty()
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("发送", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
