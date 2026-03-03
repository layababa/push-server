package com.layababateam.pushserver.service.push

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * FCM 推送通道 — 通過 Firebase Admin SDK 發送推送通知
 *
 * 重構自 FcmPushService，實現統一 PushChannel 介面。
 */
@Service
@ConditionalOnProperty("fcm.enabled", havingValue = "true", matchIfMissing = false)
class FcmPushChannel : PushChannel {

    private val log = LoggerFactory.getLogger(javaClass)

    override val channelType: String = "fcm"

    override fun send(
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>?
    ): Boolean {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp 未初始化，無法發送推送")
            return false
        }

        if (pushToken.isBlank()) {
            log.warn("pushToken 為空，跳過 FCM 推送")
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

            if (!data.isNullOrEmpty()) {
                messageBuilder.putAllData(data)
            }

            val messageId = FirebaseMessaging.getInstance().send(messageBuilder.build())
            log.info("FCM 推送成功: messageId={}, token={}...", messageId, pushToken.take(20))
            true
        } catch (e: FirebaseMessagingException) {
            log.error(
                "FCM 推送失敗: errorCode={}, token={}..., err={}",
                e.messagingErrorCode, pushToken.take(20), e.message
            )
            false
        } catch (e: Exception) {
            log.error("FCM 推送異常: token={}..., err={}", pushToken.take(20), e.message, e)
            false
        }
    }
}
