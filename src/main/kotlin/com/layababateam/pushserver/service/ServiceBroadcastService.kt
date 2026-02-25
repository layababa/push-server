package com.layababateam.pushserver.service

import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.ServiceSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 服务广播推送 — 向某个公共服务的所有订阅者发送 FCM 推送
 */
@Service
class ServiceBroadcastService(
    private val subscriptionRepo: ServiceSubscriptionRepository,
    private val deviceRepo: DeviceBindingRepository,
    private val fcmPushService: FcmPushService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 向指定服务的所有订阅者广播推送
     *
     * @param serviceId   服务 ID
     * @param serviceName 服务名（用于日志）
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
            val devices = deviceRepo.findByUserId(userId)
            for (device in devices) {
                val pushToken = device.pushToken
                if (pushToken.isNullOrBlank()) continue

                val data = mapOf(
                    "source" to "service",
                    "serviceName" to serviceName
                )

                val ok = fcmPushService.sendPush(
                    pushToken = pushToken,
                    title = title,
                    body = body,
                    data = data
                )
                if (ok) success++
            }
        }

        log.info("服务[{}] 广播完成: 成功={}/{}", serviceName, success, subscriberIds.size)
        return success to subscriberIds.size
    }
}
