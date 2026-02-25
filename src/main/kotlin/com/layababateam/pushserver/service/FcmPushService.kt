package com.layababateam.pushserver.service

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.FirebaseMessagingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * FCM 推送服务 — 通过 Firebase Admin SDK 向设备发送推送通知
 */
@Service
class FcmPushService(
    @Value("\${fcm.enabled:false}") private val enabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 向指定 pushToken 发送推送通知
     *
     * @param pushToken  设备的 FCM registration token
     * @param title      通知标题
     * @param body       通知内容
     * @param data       额外数据（可选）
     * @return true=发送成功, false=发送失败
     */
    fun sendPush(
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>? = null
    ): Boolean {
        if (!enabled) {
            log.debug("FCM 已禁用，跳过推送: title={}", title)
            return false
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp 未初始化，无法发送推送")
            return false
        }

        if (pushToken.isBlank()) {
            log.warn("pushToken 为空，跳过推送")
            return false
        }

        return try {
            val notification = Notification.builder()
                .setTitle(title ?: "新消息")
                .setBody(body ?: "")
                .build()

            val messageBuilder = Message.builder()
                .setToken(pushToken)
                .setNotification(notification)

            // 附加额外数据
            if (!data.isNullOrEmpty()) {
                messageBuilder.putAllData(data)
            }

            val messageId = FirebaseMessaging.getInstance().send(messageBuilder.build())
            log.info("FCM 推送成功: messageId={}, token={}...", messageId, pushToken.take(20))
            true
        } catch (e: FirebaseMessagingException) {
            log.error("FCM 推送失败: errorCode={}, token={}..., err={}",
                e.messagingErrorCode, pushToken.take(20), e.message)
            false
        } catch (e: Exception) {
            log.error("FCM 推送异常: token={}..., err={}", pushToken.take(20), e.message, e)
            false
        }
    }
}
