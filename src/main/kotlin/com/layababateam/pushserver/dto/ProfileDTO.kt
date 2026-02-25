package com.layababateam.pushserver.dto

import jakarta.validation.constraints.NotBlank

// === Profile 模块 DTO ===

// 用户资料响应（我的页面）
data class ProfileResponse(
    val uid: Long,
    val phone: String,        // 脱敏: 138****8000
    val pushKey: String,      // 专属推送 Key
    val deviceType: String?,  // 当前设备类型
    val deviceId: String?,    // 当前设备 ID
    val realNameVerified: Boolean = false  // 是否已实名认证
)

// 设备绑定请求
data class DeviceBindRequest(
    @field:NotBlank(message = "设备类型不能为空")
    val deviceType: String,

    @field:NotBlank(message = "设备ID不能为空")
    val deviceId: String,

    val pushToken: String? = null  // 厂商推送 Token（可选）
)

// 重置 pushKey 响应
data class ResetPushKeyResponse(
    val newPushKey: String
)

// 换绑手机号 — 发送验证码请求
data class PhoneSendOtpRequest(
    @field:NotBlank(message = "手机号不能为空")
    val phone: String
)

// 换绑手机号 — 执行换绑请求
data class ChangePhoneRequest(
    @field:NotBlank(message = "当前手机号验证码不能为空")
    val currentOtp: String,

    @field:NotBlank(message = "新手机号不能为空")
    val newPhone: String,

    @field:NotBlank(message = "新手机号验证码不能为空")
    val newPhoneOtp: String
)

// 换绑手机号响应
data class ChangePhoneResponse(
    val phone: String  // 脱敏后的新手机号
)

// 实名认证请求
data class RealNameVerifyRequest(
    @field:NotBlank(message = "姓名不能为空")
    val realName: String,

    @field:NotBlank(message = "身份证号不能为空")
    val idCard: String
)
