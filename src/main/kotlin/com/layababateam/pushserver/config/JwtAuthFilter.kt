package com.layababateam.pushserver.config

import com.layababateam.pushserver.service.auth.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT 认证过滤器
 * 从 Authorization header 提取 Bearer Token，验证后：
 * 1. 将 userId 注入 request attribute（供 @RequestAttribute 使用）
 * 2. 设置 SecurityContext Authentication（供 Spring Security .authenticated() 校验使用）
 */
@Component
class JwtAuthFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)

            if (jwtService.validateToken(token)) {
                val userId = jwtService.getUserIdFromToken(token)
                if (userId != null) {
                    // 注入 request attribute，供 Controller @RequestAttribute("userId") 使用
                    request.setAttribute("userId", userId)

                    // 设置 Spring Security 认证上下文，让 .authenticated() 通过
                    val auth = UsernamePasswordAuthenticationToken(
                        userId,   // principal
                        null,     // credentials
                        emptyList() // authorities
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
