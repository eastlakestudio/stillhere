package com.eastlakestudio.stillhere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eastlakestudio.stillhere.data.CareRelation

@Composable
fun CaringCard(
    relation: CareRelation,
    onClick: () -> Unit,
    onGreeting: ((String) -> Unit)? = null
) {
    val isActive = relation.isActive

    val accentColor = if (isActive)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.tertiary

    val onBgColor = if (isActive)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onTertiaryContainer

    // 柔和的容器渐变 — 左上微亮 · 右下微暗，仅比纯色多一点层次感
    val containerColor = if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.tertiaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        containerColor.copy(alpha = 0.55f),
                        containerColor
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .clickable { onClick() }
    ) {
        Column {
            // 顶部状态条 — 极淡
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.25f),
                                accentColor.copy(alpha = 0.06f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 头像 — 柔和渐变
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.55f),
                                    accentColor.copy(alpha = 0.25f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        relation.name.take(2),
                        color = onBgColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 信息区
                Column(modifier = Modifier.weight(1f)) {
                    // 第 1 行：昵称 + 关心天数
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            relation.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onBgColor
                        )
                        Text(
                            "${relation.days} 天",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = onBgColor.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 第 2 行：活动状态
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.6f))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            relation.activityText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = onBgColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 保留空白以维持卡片高度
                    Spacer(modifier = Modifier.height(26.dp))
                }

                // 右侧操作区
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 问安按钮
                    if (onGreeting != null) {
                        Text(
                            "🙏",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onGreeting(relation.bindCode) },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // 箭头
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = onBgColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 空占位卡片 — 用于预留关心人卡片位置（与 CaringCard 等高）
 */
@Composable
fun EmptyCaringPlaceholder() {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(surfaceVariant.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        surfaceVariant.copy(alpha = 0.3f),
                        surfaceVariant.copy(alpha = 0.1f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = MaterialTheme.shapes.large
            )
    ) {
        Column {
            // 顶部状态条 — 极淡
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(surfaceVariant.copy(alpha = 0.3f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 头像占位
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(surfaceVariant.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 信息区占位
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(surfaceVariant.copy(alpha = 0.4f))
                        )
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(surfaceVariant.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(surfaceVariant.copy(alpha = 0.35f))
                    )

                    Spacer(modifier = Modifier.height(26.dp))
                }

                // 箭头占位
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
