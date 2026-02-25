package com.layababateam.pushserver.service.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

/**
 * JWT Token 服务
 * - 生成/验证 JWT
 * - Redis 存储活跃 Session（支持单设备互斥和主动登出）
 */
@Service
class JwtService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration}") private val expiration: Long
) {
    companion object {
        private const val SESSION_KEY_PREFIX = "session:user:"
    }

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /**
     * 生成 JWT Token 并存入 Redis
     * 同时踢掉该用户的旧 Session（单设备互斥）
     */
    fun generateToken(userId: Long, deviceId: String): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("deviceId", deviceId)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()

        // 单设备互斥：存新 Token，覆盖旧的（旧 Token 自动失效）
        val sessionKey = "$SESSION_KEY_PREFIX$userId"
        redisTemplate.opsForValue().set(sessionKey, token, expiration, TimeUnit.MILLISECONDS)

        return token
    }

    /**
     * 从 Token 中提取 userId
     */
    fun getUserIdFromToken(token: String): Long? {
        return try {
            val claims = parseToken(token)
            claims.subject.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 验证 Token 是否有效
     * 1. JWT 签名和过期验证
     * 2. 与 Redis 中的活跃 Session 比对（确保没被踢下线）
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseToken(token)
            val userId = claims.subject

            // 检查是否是当前活跃的 Session
            val sessionKey = "$SESSION_KEY_PREFIX$userId"
            val activeToken = redisTemplate.opsForValue().get(sessionKey)

            activeToken == token
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 登出：删除 Redis 中的 Session
     */
    fun invalidateToken(userId: Long) {
        val sessionKey = "$SESSION_KEY_PREFIX$userId"
        redisTemplate.delete(sessionKey)
    }

    private fun parseToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
