package com.eastlakestudio.stillhere.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 一条关心关系
 *
 * 对应 iOS CareRelation
 */
data class CareRelation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,          // 昵称
    val bindCode: String,      // 关心码（6位）
    val bindDate: Long = System.currentTimeMillis(),
    val lastActive: Long? = null,       // 对方最近活跃时间（Unix 秒，来自服务端）
    val isCharging: Boolean = false     // 对方是否在充电
) {
    /** 关心天数（从绑定日算起，最少 1 天，仅比较日期忽略时分秒） */
    val days: Int
        get() {
            val bindLocalDate = Instant.ofEpochMilli(bindDate).atZone(ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now()
            val diff = ChronoUnit.DAYS.between(bindLocalDate, today).toInt()
            return diff.coerceAtLeast(0) + 1
        }

    /** 活动状态文案 */
    val activityText: String
        get() {
            val ts = lastActive ?: return "暂无活动"
            val secondsAgo = (System.currentTimeMillis() / 1000) - ts
            return when {
                secondsAgo < 60 -> "刚刚活跃"
                secondsAgo < 3600 -> "${secondsAgo / 60} 分钟前活跃"
                secondsAgo < 86400 -> "${secondsAgo / 3600} 小时前活跃"
                else -> "${secondsAgo / 86400} 天前活跃"
            }
        }

    /** 上次活动相对时间（不含"活跃"后缀，用于卡片第三行） */
    val lastActiveText: String
        get() {
            val ts = lastActive ?: return "暂无活动记录"
            val secondsAgo = (System.currentTimeMillis() / 1000) - ts
            return when {
                secondsAgo < 60 -> "刚刚"
                secondsAgo < 3600 -> "${secondsAgo / 60} 分钟前"
                secondsAgo < 86400 -> "${secondsAgo / 3600} 小时前"
                else -> "${secondsAgo / 86400} 天前"
            }
        }

    val isActive: Boolean
        get() {
            val ts = lastActive ?: return false
            return (System.currentTimeMillis() / 1000) - ts < 86400 // 24小时内算活跃
        }
}

/**
 * 关心关系本地持久化管理
 *
 * 对应 iOS CareStore ObservableObject
 */
class CareStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anhao.care", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main)

    /** 被关心人数（来自服务端心跳响应，每次上报更新） */
    val caredByCountFlow: StateFlow<Int> = Reporter.caredByCount

    /** 我关心的人（关心列表） */
    private val _caring = MutableStateFlow<List<CareRelation>>(emptyList())
    val caring: StateFlow<List<CareRelation>> = _caring.asStateFlow()

    init {
        load()
        syncFromServer()
    }

    // MARK: - 从服务端恢复

    /** 新装 App 或清理数据后，从服务端拉回关心关系 */
    private fun syncFromServer() {
        scope.launch(Dispatchers.IO) {
            try {
                val remote = Reporter.fetchCaring()
                if (remote.isEmpty()) return@launch
                val existingCodes = _caring.value.map { it.bindCode }.toSet()
                val newOnes = remote.filter { it.bindCode !in existingCodes }
                if (newOnes.isEmpty()) return@launch
                _caring.value = _caring.value + newOnes.map {
                    CareRelation(name = it.bindCode, bindCode = it.bindCode)
                }
                save()
                android.util.Log.d("CareStore", "synced ${newOnes.size} relations from server")
            } catch (e: Exception) {
                android.util.Log.e("CareStore", "syncFromServer failed: ${e.message}")
            }
        }
    }

    // MARK: - 我关心

    fun addCaring(name: String, bindCode: String) {
        _caring.value = _caring.value + CareRelation(name = name, bindCode = bindCode)
        save()
        scope.launch {
            Reporter.registerCare(bindCode)
        }
        com.eastlakestudio.stillhere.StillHereApp.instance.syncToCloud()
    }

    fun removeCaring(relation: CareRelation) {
        _caring.value = _caring.value.filter { it.id != relation.id }
        save()
        scope.launch {
            Reporter.unregisterCare(relation.bindCode)
        }
        com.eastlakestudio.stillhere.StillHereApp.instance.syncToCloud()
    }

    fun updateCaringName(relation: CareRelation, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _caring.value = _caring.value.map {
            if (it.id == relation.id) it.copy(name = trimmed) else it
        }
        save()
        com.eastlakestudio.stillhere.StillHereApp.instance.syncToCloud()
    }

    fun updateCaringNameByCode(bindCode: String, name: String) {
        val existing = _caring.value.find { it.bindCode == bindCode }
        if (existing != null) {
            _caring.value = _caring.value.map {
                if (it.bindCode == bindCode) it.copy(name = name) else it
            }
            save()
        }
    }

    // MARK: - 活动状态刷新

    /** 拉取被关心者的最近活动状态 */
    fun refreshCaredStatus() {
        val codes = _caring.value.map { it.bindCode }.distinct()
        if (codes.isEmpty()) return
        scope.launch {
            try {
                val statuses = Reporter.fetchCaredStatus(codes)
                _caring.value = _caring.value.map { rel ->
                    val s = statuses[rel.bindCode]
                    if (s != null) {
                        rel.copy(lastActive = s.lastActive, isCharging = s.isCharging)
                    } else {
                        rel
                    }
                }
                save()
            } catch (e: Exception) {
                android.util.Log.e("CareStore", "refreshCaredStatus failed: ${e.message}")
            }
        }
    }

    // MARK: - 聚合

    val caringCount: Int get() = _caring.value.size

    /** 被关心天数（基于服务端返回的被关心人数计算起始日） */
    fun totalCaredDays(count: Int): Int {
        if (count <= 0) return 0
        val earliest = _caring.value.minOfOrNull { it.bindDate } ?: System.currentTimeMillis()
        return daysSince(earliest)
    }

    val totalCaringDays: Int
        get() {
            val earliest = _caring.value.minOfOrNull { it.bindDate } ?: return 0
            return daysSince(earliest)
        }

    private fun daysSince(timestamp: Long): Int {
        val bindCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()
        var days = 0
        while (bindCal.before(nowCal)) {
            bindCal.add(Calendar.DAY_OF_MONTH, 1)
            days++
        }
        return maxOf(days, 0) + 1
    }

    // MARK: - 持久化

    private fun save() {
        prefs.edit()
            .putString("caring", gson.toJson(_caring.value))
            .apply()
    }

    private fun load() {
        val type = object : TypeToken<List<CareRelation>>() {}.type
        try {
            prefs.getString("caring", null)?.let {
                _caring.value = gson.fromJson(it, type)
            }
        } catch (_: Exception) { }
    }
}
