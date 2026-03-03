package com.layababateam.pushserver.service.push

/**
 * 統一推送通道介面 — 所有廠商推送實現此介面
 *
 * Strategy 模式：PushChannelRouter 根據 DeviceBinding.pushChannel 路由到對應實現。
 */
interface PushChannel {

    /** 通道標識，與 DeviceBinding.pushChannel 欄位值一致 */
    val channelType: String

    /**
     * 向指定 token 發送推送通知
     *
     * @param pushToken  設備的推送 token（FCM registration token 或廠商 token）
     * @param title      通知標題
     * @param body       通知內容
     * @param data       額外數據（可選，透傳給客戶端）
     * @return true=發送成功, false=發送失敗
     */
    fun send(
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>? = null
    ): Boolean
}
