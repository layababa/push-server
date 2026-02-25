package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.entity.DeviceBinding
import com.layababateam.pushserver.entity.User
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.UserRepository
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * 用户资料 Controller
 * 提供「我的」页面所需的全部接口，均需 JWT 认证
 */
@RestController
@RequestMapping("/api/v1/user")
class ProfileController(
    private val userRepo: UserRepository,
    private val deviceRepo: DeviceBindingRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 获取用户资料
     * GET /api/v1/user/profile
     */
    @GetMapping("/profile")
    fun getProfile(@RequestAttribute("userId") userId: Long): ApiResult<ProfileResponse> {
        val user = userRepo.findById(userId).orElse(null)
            ?: return ApiResult.fail("用户不存在", ApiResult.CODE_NOT_FOUND)

        // 老用户懒生成 pushKey 并持久化
        val pushKey = user.ensurePushKey()
        if (user.pushKey != null) {
            userRepo.save(user)
        }

        // 查找当前用户最近活跃的设备
        val devices = deviceRepo.findByUserId(userId)
        val latestDevice = devices.maxByOrNull { it.lastActive }

        return ApiResult.ok(
            ProfileResponse(
                uid = user.id!!,
                phone = maskPhone(user.phone ?: ""),
                pushKey = pushKey,
                deviceType = latestDevice?.deviceType,
                deviceId = latestDevice?.deviceUuid
            )
        )
    }

    /**
     * 重置推送 Key
     * POST /api/v1/user/webhook/reset
     */
    @PostMapping("/webhook/reset")
    fun resetPushKey(@RequestAttribute("userId") userId: Long): ApiResult<ResetPushKeyResponse> {
        val user = userRepo.findById(userId).orElse(null)
            ?: return ApiResult.fail("用户不存在", ApiResult.CODE_NOT_FOUND)

        val newKey = User.generatePushKey()
        user.pushKey = newKey
        userRepo.save(user)

        log.info("User {} reset pushKey", userId)
        return ApiResult.ok(ResetPushKeyResponse(newPushKey = newKey), "推送 Key 已重置")
    }

    /**
     * 绑定设备推送 Token
     * POST /api/v1/user/device/bind
     */
    @PostMapping("/device/bind")
    fun bindDevice(
        @RequestAttribute("userId") userId: Long,
        @Valid @RequestBody req: DeviceBindRequest
    ): ApiResult<Nothing> {
        val existing = deviceRepo.findByDeviceUuid(req.deviceId)

        if (existing.isPresent) {
            val device = existing.get()
            device.userId = userId
            device.deviceType = req.deviceType
            device.pushToken = req.pushToken ?: device.pushToken
            device.lastActive = LocalDateTime.now()
            deviceRepo.save(device)
        } else {
            val device = DeviceBinding(
                deviceUuid = req.deviceId,
                userId = userId,
                deviceType = req.deviceType,
                pushToken = req.pushToken
            )
            deviceRepo.save(device)
        }

        log.info("User {} bound device: type={}, id={}", userId, req.deviceType, req.deviceId)
        return ApiResult.ok(msg = "设备绑定成功")
    }

    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return "${phone.take(3)}****${phone.takeLast(4)}"
    }
}
