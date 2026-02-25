package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.entity.DeviceBinding
import com.layababateam.pushserver.entity.User
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.UserRepository
import com.layababateam.pushserver.service.AccountDeletionService
import com.layababateam.pushserver.service.PhoneChangeService
import com.layababateam.pushserver.service.auth.JwtService
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
    private val deviceRepo: DeviceBindingRepository,
    private val accountDeletionService: AccountDeletionService,
    private val phoneChangeService: PhoneChangeService,
    private val jwtService: JwtService
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
                deviceId = latestDevice?.deviceUuid,
                realNameVerified = user.realNameVerified
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

    /**
     * 注销账号
     * POST /api/v1/user/account/delete
     * 合规要求：个人信息保护法第47条
     */
    @PostMapping("/account/delete")
    fun deleteAccount(@RequestAttribute("userId") userId: Long): ApiResult<Nothing> {
        val deleted = accountDeletionService.deleteAccount(userId)
        if (!deleted) {
            return ApiResult.fail("用户不存在", ApiResult.CODE_NOT_FOUND)
        }

        // 使 JWT 失效
        jwtService.invalidateToken(userId)

        log.info("Account deleted via API: userId={}", userId)
        return ApiResult.ok(msg = "账号已注销")
    }

    // ========================
    // 换绑手机号
    // ========================

    /**
     * 换绑手机号 — 发送验证码
     * POST /api/v1/user/phone/send-otp
     */
    @PostMapping("/phone/send-otp")
    fun phoneSendOtp(
        @RequestAttribute("userId") userId: Long,
        @Valid @RequestBody req: PhoneSendOtpRequest
    ): ApiResult<Nothing> {
        return try {
            phoneChangeService.sendOtp(req.phone)
            ApiResult.ok(msg = "验证码已发送")
        } catch (e: IllegalArgumentException) {
            ApiResult.fail(e.message ?: "参数错误", ApiResult.CODE_BAD_REQUEST)
        }
    }

    /**
     * 换绑手机号 — 执行换绑
     * POST /api/v1/user/phone/change
     */
    @PostMapping("/phone/change")
    fun changePhone(
        @RequestAttribute("userId") userId: Long,
        @Valid @RequestBody req: ChangePhoneRequest
    ): ApiResult<ChangePhoneResponse> {
        return try {
            val maskedPhone = phoneChangeService.changePhone(
                userId = userId,
                currentOtp = req.currentOtp,
                newPhone = req.newPhone,
                newPhoneOtp = req.newPhoneOtp
            )
            ApiResult.ok(ChangePhoneResponse(phone = maskedPhone), "手机号换绑成功")
        } catch (e: IllegalArgumentException) {
            ApiResult.fail(e.message ?: "参数错误", ApiResult.CODE_BAD_REQUEST)
        } catch (e: IllegalStateException) {
            ApiResult.fail(e.message ?: "操作失败", ApiResult.CODE_CONFLICT)
        }
    }

    // ========================
    // 实名认证
    // ========================

    /**
     * 实名认证
     * POST /api/v1/user/realname/verify
     */
    @PostMapping("/realname/verify")
    fun verifyRealName(
        @RequestAttribute("userId") userId: Long,
        @Valid @RequestBody req: RealNameVerifyRequest
    ): ApiResult<Nothing> {
        val user = userRepo.findById(userId).orElse(null)
            ?: return ApiResult.fail("用户不存在", ApiResult.CODE_NOT_FOUND)

        // 已认证不可重复
        if (user.realNameVerified) {
            return ApiResult.fail("已完成实名认证，不可重复认证", ApiResult.CODE_CONFLICT)
        }

        // 身份证号格式校验（18位）
        val idCardRegex = Regex("^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$")
        if (!req.idCard.matches(idCardRegex)) {
            return ApiResult.fail("身份证号格式不正确", ApiResult.CODE_BAD_REQUEST)
        }

        // 保存实名信息
        user.realName = req.realName.trim()
        user.idCard = req.idCard.trim().uppercase()
        user.realNameVerified = true
        userRepo.save(user)

        log.info("User {} completed real-name verification", userId)
        return ApiResult.ok(msg = "实名认证成功")
    }

    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return "${phone.take(3)}****${phone.takeLast(4)}"
    }
}
