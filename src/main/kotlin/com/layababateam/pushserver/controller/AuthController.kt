package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.service.auth.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {
    // 1. 发送短信验证码
    @PostMapping("/otp/send")
    fun sendOtp(@Valid @RequestBody req: SendOtpRequest): ApiResult<Nothing> {
        return try {
            authService.sendOtp(req.phone)
            ApiResult.ok(msg = "验证码已发送")
        } catch (e: IllegalArgumentException) {
            ApiResult.fail(e.message ?: "发送失败", ApiResult.CODE_BAD_REQUEST)
        }
    }

    // 2. OTP 登录
    @PostMapping("/login")
    fun login(@Valid @RequestBody req: AuthLoginRequest): ApiResult<AuthLoginResponse> {
        return try {
            val result = authService.login(req)
            ApiResult.ok(result, "登录成功")
        } catch (e: IllegalArgumentException) {
            ApiResult.fail(e.message ?: "登录失败", ApiResult.CODE_UNAUTHORIZED)
        }
    }

    // 3. 退出登录
    @PostMapping("/logout")
    fun logout(@RequestAttribute("userId") userId: Long): ApiResult<Nothing> {
        authService.logout(userId)
        return ApiResult.ok(msg = "已退出登录")
    }
}
