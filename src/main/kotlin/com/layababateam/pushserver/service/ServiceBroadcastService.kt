package com.layababateam.pushserver.service

import com.layababateam.pushserver.dto.SendMessageRequest
import com.layababateam.pushserver.repository.ServiceSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 服务广播推送 — 向某个公共服务的所有订阅者发送推送（入库 + FCM）
 */
@Service
class ServiceBroadcastService(
    private val subscriptionRepo: ServiceSubscriptionRepository,
    private val messageService: MessageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 向指定服务的所有订阅者广播推送
     *
     * 通过 MessageService.sendMessage() 发送，确保消息同时入库和推送 FCM。
     *
     * @param serviceId   服务 ID
     * @param serviceName 服务名（用于分组和扩展数据）
     * @param title       通知标题
     * @param body        通知正文
     * @return Pair(成功数, 总订阅者数)
     */
    fun broadcast(
        serviceId: Long,
        serviceName: String,
        title: String,
        body: String
    ): Pair<Int, Int> {
        val subscriberIds = subscriptionRepo.findUserIdsByServiceId(serviceId)
        if (subscriberIds.isEmpty()) {
            log.debug("服务[{}]无订阅者，跳过广播", serviceName)
            return 0 to 0
        }

        log.info("服务[{}] 开始广播推送: 订阅者={}, title={}", serviceName, subscriberIds.size, title)

        var success = 0
        for (userId in subscriberIds) {
            try {
                val req = SendMessageRequest(
                    receiverId = userId,
                    title = title,
                    content = body,
                    group = serviceName,
                    msgType = "notification",
                    extraData = mapOf(
                        "source" to "service",
                        "serviceName" to serviceName
                    )
                )
                messageService.sendMessage(senderId = null, req = req)
                success++
            } catch (e: Exception) {
                log.warn("服务[{}] 推送给用户 {} 失败: {}", serviceName, userId, e.message)
            }
        }

        log.info("服务[{}] 广播完成: 成功={}/{}", serviceName, success, subscriberIds.size)
        return success to subscriberIds.size
    }
}
