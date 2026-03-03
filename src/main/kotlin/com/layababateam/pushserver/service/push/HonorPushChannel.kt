package com.layababateam.pushserver.service.push

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 榮耀 Honor Push 推送通道 — 透過榮耀推送 HTTP API 發送通知
 *
 * API 文檔：https://developer.honor.com/cn/docs/11002/guides/introduction
 * 認證方式：OAuth2 client_credentials → Bearer Token（同華為模式）
 */
@Service
@ConditionalOnProperty("push.honor.enabled", havingValue = "true", matchIfMissing = false)
class HonorPushChannel(
    @Value("\${push.honor.app-id}") private val appId: String,
    @Value("\${push.honor.client-id}") private val clientId: String,
    @Value("\${push.honor.client-secret}") private val clientSecret: String,
) : PushChannel {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()
    private val objectMapper = ObjectMapper()

    override val channelType: String = "honor"

    companion object {
        private const val AUTH_URL = "https://oauth-login.cloud.hihonor.com/oauth2/v3/token"
        private const val PUSH_URL_TEMPLATE = "https://push-api.cloud.hihonor.com/api/v1/%s/messages:send"
        /** 提前 5 分鐘刷新 token */
        private const val TOKEN_REFRESH_BUFFER_SECONDS = 300L
    }

    private var accessToken: String? = null
    private var tokenExpireAt: Instant = Instant.EPOCH
    private val tokenLock = ReentrantLock()

    override fun send(
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>?,
    ): Boolean {
        if (pushToken.isBlank()) {
            log.warn("pushToken 為空，跳過榮耀推送")
            return false
        }

        val token = getAccessToken()
        if (token == null) {
            log.error("榮耀推送：無法獲取 access_token，跳過推送")
            return false
        }

        return try {
            val pushUrl = PUSH_URL_TEMPLATE.format(appId)

            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
            }

            val messagePayload = buildMessagePayload(pushToken, title, body, data)
            val request = HttpEntity(messagePayload, headers)
            val response = restTemplate.postForEntity(pushUrl, request, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                log.info("榮耀推送成功: token={}..., response={}", pushToken.take(20), response.body?.take(200))
                true
            } else {
                log.error("榮耀推送失敗: httpStatus={}, token={}..., response={}", response.statusCode, pushToken.take(20), response.body?.take(200))
                false
            }
        } catch (e: Exception) {
            log.error("榮耀推送異常: token={}..., err={}", pushToken.take(20), e.message, e)
            false
        }
    }

    private fun buildMessagePayload(
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>?,
    ): String {
        val message = mutableMapOf<String, Any>(
            "token" to listOf(pushToken),
            "android" to mapOf(
                "notification" to mapOf(
                    "title" to (title ?: "新消息"),
                    "body" to (body ?: ""),
                    "click_action" to mapOf("type" to 3), // 3=打開應用首頁
                ),
            ),
        )

        if (!data.isNullOrEmpty()) {
            message["data"] = objectMapper.writeValueAsString(data)
        }

        return objectMapper.writeValueAsString(mapOf("message" to message))
    }

    /**
     * 獲取 OAuth2 access_token（帶本地快取 + 自動刷新）
     */
    private fun getAccessToken(): String? {
        if (accessToken != null && Instant.now().isBefore(tokenExpireAt)) {
            return accessToken
        }

        return tokenLock.withLock {
            if (accessToken != null && Instant.now().isBefore(tokenExpireAt)) {
                return@withLock accessToken
            }

            try {
                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_FORM_URLENCODED
                }

                val params = LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "client_credentials")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                val request = HttpEntity(params, headers)
                val response = restTemplate.postForEntity(AUTH_URL, request, Map::class.java)

                if (response.statusCode.is2xxSuccessful && response.body != null) {
                    val responseBody = response.body!!
                    val newToken = responseBody["access_token"] as? String
                    val expiresIn = (responseBody["expires_in"] as? Number)?.toLong() ?: 3600L

                    if (newToken != null) {
                        accessToken = newToken
                        tokenExpireAt = Instant.now().plusSeconds(expiresIn - TOKEN_REFRESH_BUFFER_SECONDS)
                        log.info("榮耀 OAuth2 token 刷新成功，有效期 {}s", expiresIn)
                        return@withLock newToken
                    }
                }

                log.error("榮耀 OAuth2 token 獲取失敗: status={}", response.statusCode)
                null
            } catch (e: Exception) {
                log.error("榮耀 OAuth2 token 獲取異常: err={}", e.message, e)
                null
            }
        }
    }
}
