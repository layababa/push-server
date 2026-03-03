package com.layababateam.pushserver.service.push

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 推送通道路由器 — 根據 pushChannel 標識分發到對應廠商實現
 *
 * 透過 Spring 自動注入所有 PushChannel Bean，按 channelType 建立路由表。
 * 未知通道或未啟用的通道回退到 FCM（如果可用）。
 */
@Service
class PushChannelRouter(channels: List<PushChannel>) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val channelMap: Map<String, PushChannel> = channels.associateBy { it.channelType }

    init {
        log.info("推送通道路由器初始化完成，已註冊通道: {}", channelMap.keys)
    }

    /**
     * 向指定設備發送推送
     *
     * @param pushChannel 推送通道標識（fcm/huawei/xiaomi/oppo/vivo/honor）
     * @param pushToken   設備推送 token
     * @param title       通知標題
     * @param body        通知內容
     * @param data        額外數據
     * @return true=發送成功, false=發送失敗
     */
    fun send(
        pushChannel: String,
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>? = null
    ): Boolean {
        val channel = channelMap[pushChannel]
        if (channel == null) {
            // 嘗試回退到 FCM
            val fallback = channelMap["fcm"]
            if (fallback != null) {
                log.warn("未知推送通道 '{}'，回退到 FCM", pushChannel)
                return fallback.send(pushToken, title, body, data)
            }
            log.error("未知推送通道 '{}'，且 FCM 回退不可用，跳過推送", pushChannel)
            return false
        }

        return channel.send(pushToken, title, body, data)
    }

    /** 檢查指定通道是否可用 */
    fun isChannelAvailable(pushChannel: String): Boolean = channelMap.containsKey(pushChannel)

    /** 取得所有已註冊的通道標識 */
    fun availableChannels(): Set<String> = channelMap.keys
}
