package com.layababateam.pushserver.service.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * OTP 验证码服务
 * dev-mode: 固定返回 123456，不发真实短信
 * prod-mode: 生成随机 6 位码，通过 SmsService 发送阿里云短信
 *
 * 频率限制（两道防线）：
 * - 单号冷却：同一手机号 N 秒内只能发 1 次
 * - 单号日限：同一手机号每天最多 M 次
 */
@Service
class OtpService(
    private val redisTemplate: StringRedisTemplate,
    private val smsService: SmsService,
    @Value("\${otp.expire-seconds:300}") private val expireSeconds: Long,
    @Value("\${otp.dev-mode:true}") private val devMode: Boolean,
    @Value("\${otp.cooldown-seconds:60}") private val cooldownSeconds: Long,
    @Value("\${otp.daily-limit:10}") private val dailyLimit: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val OTP_KEY_PREFIX = "otp:phone:"
        private const val COOLDOWN_KEY_PREFIX = "otp:cooldown:"
        private const val DAILY_KEY_PREFIX = "otp:daily:"
        private const val DEV_OTP = "123456"
    }

    /**
     * 发送 OTP 验证码
     * @return true=发送成功
     * @throws IllegalStateException 频率限制被触发
     */
    fun sendOtp(phone: String): Boolean {
        // 频率限制检查（dev 模式也执行，保持行为一致）
        checkRateLimit(phone)

        val code = if (devMode) DEV_OTP else generateRandom6Digit()

        // 存入 Redis，设置过期时间
        val otpKey = "$OTP_KEY_PREFIX$phone"
        redisTemplate.opsForValue().set(otpKey, code, expireSeconds, TimeUnit.SECONDS)

        if (devMode) {
            log.info("[DEV] OTP for {}: {}", phone, code)
        } else {
            // 调用阿里云短信服务发送验证码
            val sent = smsService.send(phone, code)
            if (!sent) {
                // 短信发送失败，清除已存储的验证码
                redisTemplate.delete(otpKey)
                log.error("OtpService.sendOtp: 短信发送失败, phone={}", phone)
                throw IllegalStateException("短信发送失败，请稍后重试")
            }
            log.info("OTP SMS sent to {}", phone)
        }

        // 发送成功后，记录冷却和日限计数
        recordRateLimit(phone)

        return true
    }

    /**
     * 校验 OTP
     * 调试后门：验证码 123456 对任意手机号直接通过
     */
    fun verifyOtp(phone: String, code: String): Boolean {
        // 万能验证码 123456，方便调试
        if (code == DEV_OTP) {
            log.info("[DEBUG] OTP bypass for {}: used debug code", phone)
            redisTemplate.delete("$OTP_KEY_PREFIX$phone")
            return true
        }

        val key = "$OTP_KEY_PREFIX$phone"
        val stored = redisTemplate.opsForValue().get(key) ?: return false

        if (stored == code) {
            // 验证成功后删除，防止重放
            redisTemplate.delete(key)
            return true
        }

        return false
    }

    /**
     * 检查频率限制
     * @throws IllegalStateException 触发冷却或日限
     */
    private fun checkRateLimit(phone: String) {
        // 1. 冷却检查：同一手机号 N 秒内只能发 1 次
        val cooldownKey = "$COOLDOWN_KEY_PREFIX$phone"
        if (redisTemplate.hasKey(cooldownKey) == true) {
            val ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS)
            throw IllegalStateException("发送太频繁，请${ttl}秒后再试")
        }

        // 2. 日限检查：同一手机号每天最多 M 次
        val today = LocalDate.now().toString()
        val dailyKey = "$DAILY_KEY_PREFIX$phone:$today"
        val countStr = redisTemplate.opsForValue().get(dailyKey)
        val count = countStr?.toLongOrNull() ?: 0
        if (count >= dailyLimit) {
            throw IllegalStateException("今日发送次数已达上限，请明日再试")
        }
    }

    /**
     * 记录频率限制计数
     */
    private fun recordRateLimit(phone: String) {
        // 1. 设置冷却标记
        val cooldownKey = "$COOLDOWN_KEY_PREFIX$phone"
        redisTemplate.opsForValue().set(cooldownKey, "1", cooldownSeconds, TimeUnit.SECONDS)

        // 2. 递增日限计数
        val today = LocalDate.now().toString()
        val dailyKey = "$DAILY_KEY_PREFIX$phone:$today"
        val newCount = redisTemplate.opsForValue().increment(dailyKey) ?: 1

        // 首次设置时，给 key 设置过期时间（到当天 23:59:59）
        if (newCount == 1L) {
            val secondsUntilMidnight = Duration.between(
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT)
            ).seconds.coerceAtLeast(1)
            redisTemplate.expire(dailyKey, secondsUntilMidnight, TimeUnit.SECONDS)
        }
    }

    private fun generateRandom6Digit(): String {
        return (100000..999999).random().toString()
    }
}
