package com.layababateam.pushserver.service

import com.layababateam.pushserver.repository.AppMessageRepository
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.ServiceSubscriptionRepository
import com.layababateam.pushserver.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 账号注销服务
 * 职责：级联删除用户的全部关联数据
 * 合规要求：个人信息保护法第47条
 */
@Service
class AccountDeletionService(
    private val userRepo: UserRepository,
    private val messageRepo: AppMessageRepository,
    private val deviceRepo: DeviceBindingRepository,
    private val subscriptionRepo: ServiceSubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 删除用户账号及所有关联数据
     * 删除顺序：subscriptions → devices → messages → user
     * @return true 删除成功, false 用户不存在
     */
    @Transactional
    fun deleteAccount(userId: Long): Boolean {
        val user = userRepo.findById(userId).orElse(null) ?: return false

        // 1. 删除服务订阅
        val subs = subscriptionRepo.findByUserId(userId)
        if (subs.isNotEmpty()) {
            subscriptionRepo.deleteAll(subs)
            log.info("Deleted {} subscriptions for user {}", subs.size, userId)
        }

        // 2. 删除设备绑定
        val devices = deviceRepo.findByUserId(userId)
        if (devices.isNotEmpty()) {
            deviceRepo.deleteAll(devices)
            log.info("Deleted {} device bindings for user {}", devices.size, userId)
        }

        // 3. 删除消息记录
        val msgCount = messageRepo.deleteByReceiverId(userId)
        if (msgCount > 0) {
            log.info("Deleted {} messages for user {}", msgCount, userId)
        }

        // 4. 删除用户记录
        userRepo.delete(user)
        log.info("Account deleted: userId={}, phone={}", userId, user.phone?.let { maskPhone(it) })

        return true
    }

    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return "***"
        return "${phone.take(3)}****${phone.takeLast(4)}"
    }
}
