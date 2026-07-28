package com.eastlakestudio.stillhere.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

/**
 * 远程上报器：POST 到 Cloudflare Worker，记录心跳
 *
 * 对应 iOS Reporter actor
 */
object Reporter {

    private const val DEFAULT_WORKER_URL = "https://api.padap.cn"
    private const val PREF_KEY_DEVICE_ID = "anhao.spike.deviceId"
    private const val PREF_KEY_BASE_URL = "anhao.spike.baseURL"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private lateinit var prefs: SharedPreferences

    /** 当前使用的 API 基地址 */
    var baseURL: String = DEFAULT_WORKER_URL
        private set

    /** 设备唯一标识 (即 userId) */
    val deviceId: String by lazy {
        loadOrCreateDeviceId()
    }

    /** 关心码：基于 deviceId 做 SHA-256 单向哈希派生的 6 位大写码，不可反推 */
    val careCode: String by lazy {
        deviceId.toCareCode()
    }

    /** 服务端返回的被关心人数 */
    private val _caredByCount = MutableStateFlow(0)
    val caredByCount: StateFlow<Int> = _caredByCount.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("anhao.spike", Context.MODE_PRIVATE)
        baseURL = prefs.getString(PREF_KEY_BASE_URL, null) ?: DEFAULT_WORKER_URL
    }

    fun setBaseURL(url: String?) {
        baseURL = if (url.isNullOrBlank()) DEFAULT_WORKER_URL else url
        prefs.edit().putString(PREF_KEY_BASE_URL, baseURL).apply()
    }

    /**
     * 上报唤醒事件 (heartbeat)，返回是否成功
     */
    suspend fun report(isCharging: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "userId" to deviceId,
                "careCode" to careCode,
                "isCharging" to isCharging
            )
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseURL/heartbeat")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    // 解析 caredByCount
                    val responseBody = it.body?.string()
                    try {
                        val jsonObj = gson.fromJson(responseBody, Map::class.java)
                        val count = (jsonObj["caredByCount"] as? Double)?.toInt() ?: 0
                        _caredByCount.value = count
                    } catch (_: Exception) {}
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "report failed: ${e.message}")
            false
        }
    }

    /**
     * 向服务端登记一条关心关系（不传昵称，隐私数据保留本地）
     */
    suspend fun registerCare(toCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "fromUserId" to deviceId,
                "toCode" to toCode
            )
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseURL/care")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "registerCare failed: ${e.message}")
            false
        }
    }

    /** 从服务端删除一条关心关系 */
    suspend fun unregisterCare(toCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseURL/care?fromUserId=$deviceId&toCode=$toCode")
                .delete()
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "unregisterCare failed: ${e.message}")
            false
        }
    }

    // MARK: - 告警上报

    /** 上报空闲告警（被关心者 APP 检测到本地告警时调用） */
    suspend fun reportAlert(idleMinutes: Int, isCharging: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        postJSON("/alert", mapOf(
            "userId" to deviceId,
            "careCode" to careCode,
            "idleMinutes" to idleMinutes,
            "isCharging" to isCharging
        ))
    }

    /** 取消告警（被关心者恢复活动时调用） */
    suspend fun cancelAlert(): Boolean = withContext(Dispatchers.IO) {
        postJSON("/alert/cancel", mapOf(
            "userId" to deviceId,
            "careCode" to careCode
        ))
    }

    /** 拉取待处理告警（Android 关心人轮询，替代 FCM 推送） */
    suspend fun fetchPendingAlerts(): List<PendingAlert> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseURL/pending-alerts?deviceId=$deviceId")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val body = it.body?.string() ?: return@withContext emptyList()
                    val jsonObj = gson.fromJson(body, Map::class.java)
                    val alerts = jsonObj["alerts"] as? List<*> ?: return@withContext emptyList()
                    alerts.mapNotNull { a ->
                        val m = a as? Map<*, *> ?: return@mapNotNull null
                        PendingAlert(
                            id = (m["id"] as? Double)?.toLong() ?: 0,
                            alertType = m["alertType"] as? String ?: "",
                            caredName = m["caredName"] as? String ?: "",
                            idleMinutes = (m["idleMinutes"] as? Double)?.toInt() ?: 0,
                            isCharging = m["isCharging"] as? Boolean ?: false,
                            isResolved = m["isResolved"] as? Boolean ?: false,
                            createdAt = (m["createdAt"] as? Double)?.toLong() ?: 0
                        )
                    }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "fetchPendingAlerts failed: ${e.message}")
            emptyList()
        }
    }

    private fun postJSON(path: String, bodyMap: Map<String, Any?>): Boolean {
        try {
            val json = gson.toJson(bodyMap)
            val requestBody = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseURL$path")
                .post(requestBody)
                .build()
            val response = client.newCall(request).execute()
            return response.use { it.isSuccessful }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "$path failed: ${e.message}")
            return false
        }
    }

    /**
     * 查询关心对象的最近活动状态
     */
    suspend fun fetchCaredStatus(codes: List<String>): Map<String, CaredStatus> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf("codes" to codes)
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseURL/cared-status")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val responseBody = it.body?.string() ?: return@withContext emptyMap()
                    val jsonObj = gson.fromJson(responseBody, Map::class.java)
                    val codesMap = jsonObj["codes"] as? Map<*, *> ?: return@withContext emptyMap()
                    val result = mutableMapOf<String, CaredStatus>()
                    for ((code, data) in codesMap) {
                        val d = data as? Map<*, *> ?: continue
                        result[code.toString()] = CaredStatus(
                            lastActive = (d["lastActive"] as? Double)?.toLong(),
                            isCharging = d["isCharging"] as? Boolean ?: false,
                            city = d["city"] as? String
                        )
                    }
                    result
                } else {
                    emptyMap()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "fetchCaredStatus failed: ${e.message}")
            emptyMap()
        }
    }

    // MARK: - 关注我的人

    /** 查询关注自己的关心码列表 */
    suspend fun fetchCaredByMe(careCode: String): List<CarerInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseURL/cared-by-me?careCode=$careCode")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val json = gson.fromJson(resp.body?.string(), Map::class.java)
                val carers = json["carers"] as? List<*> ?: return@withContext emptyList()
                carers.mapNotNull { c ->
                    val m = c as? Map<*, *> ?: return@mapNotNull null
                    CarerInfo(careCode = m["careCode"] as? String ?: "")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "fetchCaredByMe failed: ${e.message}")
            emptyList()
        }
    }

    // MARK: - 问安

    /** 发送问安，返回问安记录 ID */
    suspend fun sendGreeting(toCode: String, message: String = "问安"): Long? = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "fromUserId" to deviceId,
                "toCode" to toCode,
                "message" to message
            )
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseURL/greeting")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val result = gson.fromJson(resp.body?.string(), Map::class.java)
                (result["id"] as? Double)?.toLong()
            }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "sendGreeting failed: ${e.message}")
            null
        }
    }

    /** 回复问安 */
    suspend fun replyGreeting(greetingId: Long, reply: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "greetingId" to greetingId,
                "reply" to reply,
                "fromUserId" to deviceId
            )
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseURL/greeting/reply")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "replyGreeting failed: ${e.message}")
            false
        }
    }

    /** 拉取未回复的问安消息 */
    suspend fun fetchPendingGreetings(careCode: String): List<PendingGreeting> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseURL/pending-greetings?careCode=$careCode")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val json = gson.fromJson(resp.body?.string(), Map::class.java)
                val greetings = json["greetings"] as? List<*> ?: return@withContext emptyList()
                greetings.mapNotNull { g ->
                    val m = g as? Map<*, *> ?: return@mapNotNull null
                    PendingGreeting(
                        id = (m["id"] as? Double)?.toLong() ?: 0,
                        fromCareCode = m["fromCareCode"] as? String ?: "",
                        message = m["message"] as? String ?: "问安",
                        createdAt = (m["createdAt"] as? Double)?.toLong() ?: 0,
                        isReply = m["isReply"] as? Boolean ?: false
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Reporter", "fetchPendingGreetings failed: ${e.message}")
            emptyList()
        }
    }

    private fun loadOrCreateDeviceId(): String {
        val ctx = appContext
        if (ctx != null) {
            val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrEmpty()) {
                return deriveDeviceId(androidId)
            }
        }

        return UUID.randomUUID().toString()
    }

    private fun deriveDeviceId(androidId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    /** SHA-256(deviceId) → hex 第 8~13 位 → 6 位大写关心码 */
    private fun String.toCareCode(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.substring(8, 14).uppercase()
    }
}

/**
 * 被关心者的远程活动状态
 */
data class CaredStatus(
    val lastActive: Long? = null,   // Unix 秒
    val isCharging: Boolean = false,
    val city: String? = null
)

/** 服务端 pending-alerts 返回的告警条目 */
data class PendingAlert(
    val id: Long,
    val alertType: String,   // idle / offline / online
    val caredName: String,
    val idleMinutes: Int,
    val isCharging: Boolean,
    val isResolved: Boolean,
    val createdAt: Long     // Unix 秒
)

/** 关注自己的人信息 */
data class CarerInfo(
    val careCode: String
)

/** 未回复的问安消息 */
data class PendingGreeting(
    val id: Long,
    val fromCareCode: String,
    val message: String,
    val createdAt: Long,     // Unix 秒
    val isReply: Boolean = false  // true = 答复消息, false = 主动问安
)
