package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.service.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest): ApiResult<UserResponse> {
        return try {
            val user = userService.register(req)
            ApiResult.ok(user, "注册成功")
        } catch (e: IllegalArgumentException) {
            ApiResult.fail(e.message ?: "注册失败", ApiResult.CODE_BAD_REQUEST)
        }
    }
    
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody req: LoginRequest,
        httpReq: HttpServletRequest
    ): ApiResult<UserResponse> {
        return try {
            // 获取客户端IP
            val clientIp = getClientIp(httpReq)
            val user = userService.login(req, clientIp)
            ApiResult.ok(user, "登录成功")
        } catch (e: IllegalArgumentException) {
            ApiResult.fail(e.message ?: "登录失败", ApiResult.CODE_UNAUTHORIZED)
        }
    }
    
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): ApiResult<UserResponse> {
        val user = userService.getUserById(id)
        return if (user != null) {
            ApiResult.ok(user)
        } else {
            ApiResult.fail("用户不存在", ApiResult.CODE_NOT_FOUND)
        }
    }
    
    // 获取真实IP，处理代理的情况
    private fun getClientIp(request: HttpServletRequest): String? {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank() && !"unknown".equals(xForwardedFor, ignoreCase = true)) {
            // 可能有多个IP，取第一个
            return xForwardedFor.split(",")[0].trim()
        }
        
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank() && !"unknown".equals(xRealIp, ignoreCase = true)) {
            return xRealIp
        }
        
        return request.remoteAddr
    }
}
