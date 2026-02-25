package com.layababateam.pushserver.service.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * OTP 验证码服务
 * dev-mode: 固定返回 123456
 * prod-mode: 接入短信 SDK（待实现）
 */
@Service
class OtpService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${otp.expire-seconds:300}") private val expireSeconds: Long,
    @Value("\${otp.dev-mode:true}") private val devMode: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val OTP_KEY_PREFIX = "otp:phone:"
        private const val DEV_OTP = "123456"
    }

    /**
     * 发送 OTP 验证码
     * @return true=发送成功
     */
    fun sendOtp(phone: String): Boolean {
        val code = if (devMode) DEV_OTP else generateRandom6Digit()

        // 存入 Redis，设置过期时间
        val key = "$OTP_KEY_PREFIX$phone"
        redisTemplate.opsForValue().set(key, code, expireSeconds, TimeUnit.SECONDS)

        if (devMode) {
            log.info("[DEV] OTP for {}: {}", phone, code)
        } else {
            // TODO: 接入真实 SMS SDK（阿里云/腾讯云短信）
            log.info("SMS sent to {}", phone)
        }

        return true
    }

    /**
     * 校验 OTP
     */
    fun verifyOtp(phone: String, code: String): Boolean {
        val key = "$OTP_KEY_PREFIX$phone"
        val stored = redisTemplate.opsForValue().get(key) ?: return false

        if (stored == code) {
            // 验证成功后删除，防止重放
            redisTemplate.delete(key)
            return true
        }

        return false
    }

    private fun generateRandom6Digit(): String {
        return (100000..999999).random().toString()
    }
}
