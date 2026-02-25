package com.layababateam.pushserver.service

import com.layababateam.pushserver.dto.ServiceItemResponse
import com.layababateam.pushserver.dto.ServiceListResponse
import com.layababateam.pushserver.entity.ServiceSubscription
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.PublicServiceRepository
import com.layababateam.pushserver.repository.ServiceSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubscriptionService(
    private val serviceRepo: PublicServiceRepository,
    private val subscriptionRepo: ServiceSubscriptionRepository,
    private val deviceRepo: DeviceBindingRepository,
    private val fcmPushService: FcmPushService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 获取所有启用的公共服务，标记当前用户是否已订阅
     */
    fun listServices(userId: Long): ServiceListResponse {
        val allServices = serviceRepo.findByActiveTrue()
        val subscribedServiceIds = subscriptionRepo.findByUserId(userId)
            .map { it.serviceId }
            .toSet()

        val items = allServices.map { svc ->
            ServiceItemResponse(
                id = svc.id ?: 0,
                serviceCode = svc.serviceCode,
                name = svc.name,
                description = svc.description,
                iconUrl = svc.iconUrl,
                isSubscribed = subscribedServiceIds.contains(svc.id)
            )
        }

        return ServiceListResponse(items = items)
    }

    /**
     * 订阅服务（幂等：重复订阅不报错）
     * @return true 表示服务存在且处理成功，false 表示服务不存在
     */
    @Transactional
    fun subscribe(userId: Long, serviceId: Long): Boolean {
        val service = serviceRepo.findById(serviceId).orElse(null)
            ?: return false

        if (!service.active) {
            return false
        }

        // 幂等：已订阅则跳过
        if (subscriptionRepo.existsByUserIdAndServiceId(userId, serviceId)) {
            log.debug("User {} already subscribed to service {}, skip", userId, serviceId)
            return true
        }

        val subscription = ServiceSubscription(
            userId = userId,
            serviceId = serviceId
        )
        subscriptionRepo.save(subscription)
        log.info("User {} subscribed to service {} ({})", userId, serviceId, service.name)

        // 发送订阅成功确认推送
        sendSubscriptionConfirmation(userId, service.name)

        return true
    }

    /**
     * 给用户发送订阅成功的 FCM 确认推送
     */
    private fun sendSubscriptionConfirmation(userId: Long, serviceName: String) {
        try {
            val devices = deviceRepo.findByUserId(userId)
            for (device in devices) {
                val pushToken = device.pushToken
                if (pushToken.isNullOrBlank()) continue

                fcmPushService.sendPush(
                    pushToken = pushToken,
                    title = "订阅成功",
                    body = "你已成功订阅「$serviceName」，后续将收到该服务的推送通知。",
                    data = mapOf(
                        "source" to "service",
                        "serviceName" to serviceName,
                        "type" to "subscription_confirm"
                    )
                )
            }
            log.info("已向用户 {} 发送「{}」订阅确认推送", userId, serviceName)
        } catch (e: Exception) {
            // 确认推送失败不影响订阅本身
            log.warn("订阅确认推送失败 (user={}, service={}): {}", userId, serviceName, e.message)
        }
    }

    /**
     * 取消订阅（幂等：未订阅则跳过）
     * @return true 表示服务存在且处理成功，false 表示服务不存在
     */
    @Transactional
    fun unsubscribe(userId: Long, serviceId: Long): Boolean {
        val service = serviceRepo.findById(serviceId).orElse(null)
            ?: return false

        subscriptionRepo.deleteByUserIdAndServiceId(userId, serviceId)
        log.info("User {} unsubscribed from service {} ({})", userId, serviceId, service.name)
        return true
    }
}
