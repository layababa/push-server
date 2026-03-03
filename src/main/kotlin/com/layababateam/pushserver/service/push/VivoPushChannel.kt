package com.layababateam.pushserver.service.push

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * VIVO Push 推送通道 — 透過 VIVO 推送 HTTP API 發送通知
 *
 * API 文檔：https://dev.vivo.com.cn/documentCenter/doc/362
 * 認證方式：AppId + AppKey + AppSecret MD5 簽名 → 換取 authToken
 */
@Service
@ConditionalOnProperty("push.vivo.enabled", havingValue = "true", matchIfMissing = false)
class VivoPushChannel(
    @Value("\${push.vivo.app-id}") private val appId: String,
    @Value("\${push.vivo.app-key}") private val appKey: String,
    @Value("\${push.vivo.app-secret}") private val appSecret: String,
) : PushChannel {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()
    private val objectMapper = ObjectMapper()

    override val channelType: String = "vivo"

    companion object {
        private const val AUTH_URL = "https://api-push.vivo.com.cn/message/auth"
        private const val PUSH_URL = "https://api-push.vivo.com.cn/message/send"
        /** 提前 5 分鐘刷新 token */
        private const val TOKEN_REFRESH_BUFFER_SECONDS = 300L
    }

    private var authToken: String? = null
    private var tokenExpireAt: Instant = Instant.EPOCH
    private val tokenLock = ReentrantLock()

    override fun send(
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>?,
    ): Boolean {
        if (pushToken.isBlank()) {
            log.warn("pushToken 為空，跳過 VIVO 推送")
            return false
        }

        val token = getAuthToken()
        if (token == null) {
            log.error("VIVO 推送：無法獲取 authToken，跳過推送")
            return false
        }

        return try {
            val headers = HttpHeaders().apply {
                set("authToken", token)
                contentType = MediaType.APPLICATION_JSON
            }

            val payload = mapOf(
                "regId" to pushToken,
                "notifyType" to 4, // 4=通知欄消息
                "title" to (title ?: "新消息"),
                "content" to (body ?: ""),
                "skipType" to 1, // 1=打開首頁
                "requestId" to System.currentTimeMillis().toString(),
            )

            val request = HttpEntity(objectMapper.writeValueAsString(payload), headers)
            val response = restTemplate.postForEntity(PUSH_URL, request, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                log.info("VIVO 推送成功: token={}..., response={}", pushToken.take(20), response.body?.take(200))
                true
            } else {
                log.error("VIVO 推送失敗: httpStatus={}, token={}..., response={}", response.statusCode, pushToken.take(20), response.body?.take(200))
                false
            }
        } catch (e: Exception) {
            log.error("VIVO 推送異常: token={}..., err={}", pushToken.take(20), e.message, e)
            false
        }
    }

    /**
     * 獲取 VIVO authToken（帶本地快取 + 自動刷新）
     *
     * 簽名規則：MD5(appId + appKey + timestamp + appSecret)
     */
    private fun getAuthToken(): String? {
        if (authToken != null && Instant.now().isBefore(tokenExpireAt)) {
            return authToken
        }

        return tokenLock.withLock {
            if (authToken != null && Instant.now().isBefore(tokenExpireAt)) {
                return@withLock authToken
            }

            try {
                val timestamp = System.currentTimeMillis()
                val sign = md5("$appId$appKey${timestamp}$appSecret")

                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }

                val body = objectMapper.writeValueAsString(
                    mapOf(
                        "appId" to appId,
                        "appKey" to appKey,
                        "timestamp" to timestamp,
                        "sign" to sign,
                    )
                )

                val request = HttpEntity(body, headers)
                val response = restTemplate.postForEntity(AUTH_URL, request, Map::class.java)

                if (response.statusCode.is2xxSuccessful && response.body != null) {
                    val responseBody = response.body!!
                    val result = responseBody["result"] as? Int
                    if (result == 0) {
                        val newToken = responseBody["authToken"] as? String
                        if (newToken != null) {
                            authToken = newToken
                            // VIVO authToken 預設 24 小時有效
                            tokenExpireAt = Instant.now().plusSeconds(86400L - TOKEN_REFRESH_BUFFER_SECONDS)
                            log.info("VIVO authToken 刷新成功")
                            return@withLock newToken
                        }
                    }
                }

                log.error("VIVO authToken 獲取失敗: status={}, body={}", response.statusCode, response.body)
                null
            } catch (e: Exception) {
                log.error("VIVO authToken 獲取異常: err={}", e.message, e)
                null
            }
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
