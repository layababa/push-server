package com.layababateam.pushserver.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 注册请求
data class RegisterRequest(
    @field:NotBlank(message = "用户名不能为空")
    @field:Size(min = 3, max = 50, message = "用户名3-50个字符")
    val username: String,
    
    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 6, max = 100, message = "密码6-100个字符")
    val password: String,
    
    // 手机和邮箱二选一或都填
    val phone: String? = null,
    val email: String? = null
)

// 登录请求
data class LoginRequest(
    @field:NotBlank(message = "账号不能为空")
    val account: String,  // 可以是username/phone/email
    
    @field:NotBlank(message = "密码不能为空")
    val password: String,
    
    @field:NotBlank(message = "设备UUID不能为空")
    val deviceUuid: String,
    
    val deviceType: String? = null,
    val pushToken: String? = null
)

// 用户响应
data class UserResponse(
    val id: Long,
    val username: String,
    val phone: String?,
    val email: String?
)
