package com.layababateam.pushserver.dto

import jakarta.validation.constraints.NotBlank

// === Auth 模块 DTO ===

// 发送 OTP 请求
data class SendOtpRequest(
    @field:NotBlank(message = "手机号不能为空")
    val phone: String
)

// OTP 登录请求
data class AuthLoginRequest(
    @field:NotBlank(message = "手机号不能为空")
    val phone: String,

    @field:NotBlank(message = "验证码不能为空")
    val otpCode: String,

    @field:NotBlank(message = "设备ID不能为空")
    val deviceId: String
)

// 登录响应
data class AuthLoginResponse(
    val token: String,
    val user: AuthUserItem
)

// 用户轻量信息（脱敏）
data class AuthUserItem(
    val uid: Long,
    val phone: String
)
