package com.eastlakestudio.stillhere.monitor

/**
 * 监测器统一接口
 *
 * 对应 iOS Monitor protocol
 */
interface Monitor {
    val identifier: String
    fun start()
    fun stop()
}
