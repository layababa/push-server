package com.layababateam.pushserver.service.auth

import com.layababateam.pushserver.dto.AuthLoginRequest
import com.layababateam.pushserver.dto.AuthLoginResponse
import com.layababateam.pushserver.dto.AuthUserItem
import com.layababateam.pushserver.entity.DeviceBinding
import com.layababateam.pushserver.entity.User
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Auth 业务服务
 * 职责：OTP 登录、登出、单设备互斥、设备绑定
 */
@Service
class AuthService(
    private val userRepo: UserRepository,
    private val deviceRepo: DeviceBindingRepository,
    private val otpService: OtpService,
    private val jwtService: JwtService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 发送 OTP 验证码
     */
    fun sendOtp(phone: String) {
        require(phone.matches(Regex("^1[3-9]\\d{9}$"))) { "手机号格式不正确" }
        otpService.sendOtp(phone)
    }

    /**
     * OTP 登录
     * 1. 校验验证码
     * 2. 查找或创建用户
     * 3. 绑定设备（创建/更新 device_binding 记录）
     * 4. 踢旧设备（JWT 单设备互斥由 JwtService 处理）
     * 5. 生成新 Token
     */
    @Transactional
    fun login(req: AuthLoginRequest): AuthLoginResponse {
        // 1. 校验 OTP
        require(otpService.verifyOtp(req.phone, req.otpCode)) { "验证码错误或已过期" }

        // 2. 查找或自动注册用户
        val user = userRepo.findByPhone(req.phone).orElseGet {
            val newUser = User(
                username = generateUniqueUsername(req.phone),
                password = "",  // OTP 登录不需要密码
                phone = req.phone
            )
            userRepo.save(newUser).also {
                log.info("Auto-registered new user: id={}, phone={}", it.id, maskPhone(req.phone))
            }
        }

        val userId = user.id!!

        // 3. 绑定设备（确保 FCM 推送能找到此用户的设备）
        bindDevice(userId, req.deviceId)

        // 4. 生成 Token（JwtService 内部处理单设备互斥）
        val token = jwtService.generateToken(userId, req.deviceId)

        log.info("User login: id={}, device={}", userId, req.deviceId)

        return AuthLoginResponse(
            token = token,
            user = AuthUserItem(
                uid = userId,
                phone = maskPhone(user.phone ?: "")
            )
        )
    }

    /**
     * 绑定/更新设备信息
     * 按 deviceUuid 做 upsert：存在则更新 userId，不存在则新建
     */
    private fun bindDevice(userId: Long, deviceUuid: String) {
        val existing = deviceRepo.findByDeviceUuid(deviceUuid)

        if (existing.isPresent) {
            val device = existing.get()
            device.userId = userId
            device.lastActive = LocalDateTime.now()
            deviceRepo.save(device)
            log.debug("Device binding updated: userId={}, uuid={}", userId, deviceUuid)
        } else {
            val device = DeviceBinding(
                deviceUuid = deviceUuid,
                userId = userId
            )
            deviceRepo.save(device)
            log.info("Device binding created: userId={}, uuid={}", userId, deviceUuid)
        }
    }

    /**
     * 登出
     */
    fun logout(userId: Long) {
        jwtService.invalidateToken(userId)
        log.info("User logout: id={}", userId)
    }

    /**
     * 生成唯一用户名：user_0000, user_0000_1, user_0000_2, ...
     * 防止手机尾号相同导致 username 唯一约束冲突
     */
    private fun generateUniqueUsername(phone: String): String {
        val base = "user_${phone.takeLast(4)}"
        if (!userRepo.existsByUsername(base)) return base
        var counter = 1
        while (userRepo.existsByUsername("${base}_$counter")) {
            counter++
        }
        return "${base}_$counter"
    }

    /**
     * 手机号脱敏：138****8000
     */
    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return "${phone.take(3)}****${phone.takeLast(4)}"
    }
}
