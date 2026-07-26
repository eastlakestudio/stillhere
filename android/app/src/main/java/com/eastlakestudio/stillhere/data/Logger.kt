package com.eastlakestudio.stillhere.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import java.util.UUID

/**
 * 单条唤醒日志记录
 *
 * 对应 iOS LogEntry
 */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val source: String,        // 监测器标识：SLC / Motion / BGAppRefresh / Charging / Foreground / Alert
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,         // 事件描述
    val appState: String,      // foreground / background / inactive
    var reportedRemote: Boolean = false
)

/**
 * 本地日志记录器：内存 + SharedPreferences JSON 持久化
 *
 * 对应 iOS Logger ObservableObject
 */
class Logger(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anhao.spike.logs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val maxEntries = 1000
    private val maxAgeMs = 24 * 60 * 60 * 1000L  // 仅保留 24 小时

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _stats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stats: StateFlow<Map<String, Int>> = _stats.asStateFlow()

    init {
        loadFromDisk()
    }

    /**
     * 记录一条日志，返回创建的 entry
     */
    fun record(source: String, event: String, appState: String, reportedRemote: Boolean = false): LogEntry {
        val entry = LogEntry(
            source = source,
            event = event,
            appState = appState,
            reportedRemote = reportedRemote
        )
        val list = _entries.value.toMutableList()
        list.add(0, entry)
        filterRecent(list)
        return entry
    }

    private fun filterRecent(list: MutableList<LogEntry>) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val filtered = list.filter { it.timestamp > cutoff }
        _entries.value = if (filtered.size > maxEntries) filtered.take(maxEntries) else filtered
        _stats.value = _entries.value.groupBy { it.source }.mapValues { it.value.size }
        persist()
    }

    /**
     * 标记某条日志已成功远程上报
     */
    fun markReported(id: String) {
        val list = _entries.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(reportedRemote = true)
            _entries.value = list
            persist()
        }
    }

    fun clear() {
        _entries.value = emptyList()
        _stats.value = emptyMap()
        prefs.edit().clear().apply()
    }

    private fun persist() {
        val json = gson.toJson(_entries.value)
        prefs.edit().putString("entries", json).apply()
    }

    private fun loadFromDisk() {
        val json = prefs.getString("entries", null) ?: return
        try {
            val type = object : TypeToken<List<LogEntry>>() {}.type
            val list: List<LogEntry> = gson.fromJson(json, type)
            val cutoff = System.currentTimeMillis() - maxAgeMs
            val recent = list.filter { it.timestamp > cutoff }
            _entries.value = recent
            _stats.value = recent.groupBy { it.source }.mapValues { it.value.size }
        } catch (_: Exception) { }
    }
}
