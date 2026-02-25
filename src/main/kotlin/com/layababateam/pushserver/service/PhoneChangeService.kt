package com.layababateam.pushserver.service

import com.layababateam.pushserver.repository.UserRepository
import com.layababateam.pushserver.service.auth.OtpService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 换绑手机号服务
 * 职责：验证双端 OTP + 冲突检测 + 更新手机号
 */
@Service
class PhoneChangeService(
    private val userRepo: UserRepository,
    private val otpService: OtpService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 发送换绑验证码（复用 OTP 服务）
     */
    fun sendOtp(phone: String) {
        require(phone.matches(Regex("^1[3-9]\\d{9}$"))) { "手机号格式不正确" }
        otpService.sendOtp(phone)
    }

    /**
     * 执行换绑手机号
     * @return 脱敏后的新手机号
     * @throws IllegalArgumentException 验证码错误
     * @throws IllegalStateException 手机号已被占用
     */
    @Transactional
    fun changePhone(userId: Long, currentOtp: String, newPhone: String, newPhoneOtp: String): String {
        require(newPhone.matches(Regex("^1[3-9]\\d{9}$"))) { "新手机号格式不正确" }

        val user = userRepo.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        val currentPhone = user.phone ?: throw IllegalStateException("当前账号未绑定手机号")

        // 1. 验证当前手机号 OTP
        require(otpService.verifyOtp(currentPhone, currentOtp)) { "当前手机号验证码错误" }

        // 2. 验证新手机号 OTP
        require(otpService.verifyOtp(newPhone, newPhoneOtp)) { "新手机号验证码错误" }

        // 3. 检查新手机号是否已被其他账号占用
        val existing = userRepo.findByPhone(newPhone)
        if (existing.isPresent && existing.get().id != userId) {
            throw IllegalStateException("该手机号已被其他账号绑定")
        }

        // 4. 更新手机号
        user.phone = newPhone
        userRepo.save(user)

        log.info("User {} changed phone from {} to {}", userId, maskPhone(currentPhone), maskPhone(newPhone))
        return maskPhone(newPhone)
    }

    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return "${phone.take(3)}****${phone.takeLast(4)}"
    }
}
