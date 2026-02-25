package com.layababateam.pushserver.dto

import jakarta.validation.constraints.NotBlank

// === Profile 模块 DTO ===

// 用户资料响应（我的页面）
data class ProfileResponse(
    val uid: Long,
    val phone: String,        // 脱敏: 138****8000
    val pushKey: String,      // 专属推送 Key
    val deviceType: String?,  // 当前设备类型
    val deviceId: String?     // 当前设备 ID
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
