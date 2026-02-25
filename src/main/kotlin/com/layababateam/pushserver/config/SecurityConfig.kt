package com.layababateam.pushserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 公开接口：不需要认证
                    .requestMatchers(
                        "/api/v1/auth/otp/send",
                        "/api/v1/auth/login",
                        // Webhook 推送用 pushKey 认证，不走 JWT
                        "/push/**",
                        // H5 服务大厅静态页面
                        "/service-hall.html",
                        // 保留旧接口暂时公开（后续 deprecated）
                        "/api/user/**"
                    ).permitAll()
                    // 其他所有接口需要认证
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            // 禁用默认登录页
            .formLogin { it.disable() }
            .httpBasic { it.disable() }

        return http.build()
    }
}
