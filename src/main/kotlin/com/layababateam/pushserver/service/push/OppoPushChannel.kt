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
 * OPPO Push 推送通道 — 透過 OPPO 推送 HTTP API 發送通知
 *
 * API 文檔：https://open.oppomobile.com/new/developmentDoc/info?id=11227
 * 認證方式：AppKey + MasterSecret SHA256 簽名 → 換取 auth_token
 */
@Service
@ConditionalOnProperty("push.oppo.enabled", havingValue = "true", matchIfMissing = false)
class OppoPushChannel(
    @Value("\${push.oppo.app-key}") private val appKey: String,
    @Value("\${push.oppo.master-secret}") private val masterSecret: String,
) : PushChannel {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()
    private val objectMapper = ObjectMapper()

    override val channelType: String = "oppo"

    companion object {
        private const val AUTH_URL = "https://api.push.oppomobile.com/server/v1/auth"
        private const val PUSH_URL = "https://api.push.oppomobile.com/server/v1/message/notification/unicast"
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
            log.warn("pushToken 為空，跳過 OPPO 推送")
            return false
        }

        val token = getAuthToken()
        if (token == null) {
            log.error("OPPO 推送：無法獲取 auth_token，跳過推送")
            return false
        }

        return try {
            val headers = HttpHeaders().apply {
                set("auth_token", token)
                contentType = MediaType.APPLICATION_JSON
            }

            val notification = mapOf(
                "title" to (title ?: "新消息"),
                "sub_title" to "",
                "content" to (body ?: ""),
                "click_action_type" to 0, // 0=啟動應用
            )

            val payload = mapOf(
                "target_type" to 2, // 2=registration_id
                "target_value" to pushToken,
                "notification" to notification,
            )

            val request = HttpEntity(objectMapper.writeValueAsString(payload), headers)
            val response = restTemplate.postForEntity(PUSH_URL, request, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                log.info("OPPO 推送成功: token={}..., response={}", pushToken.take(20), response.body?.take(200))
                true
            } else {
                log.error("OPPO 推送失敗: httpStatus={}, token={}..., response={}", response.statusCode, pushToken.take(20), response.body?.take(200))
                false
            }
        } catch (e: Exception) {
            log.error("OPPO 推送異常: token={}..., err={}", pushToken.take(20), e.message, e)
            false
        }
    }

    /**
     * 獲取 OPPO auth_token（帶本地快取 + 自動刷新）
     *
     * 簽名規則：SHA256(appKey + timestamp + masterSecret)
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
                val timestamp = System.currentTimeMillis().toString()
                val sign = sha256("$appKey$timestamp$masterSecret")

                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_FORM_URLENCODED
                }

                val body = "app_key=$appKey&timestamp=$timestamp&sign=$sign"
                val request = HttpEntity(body, headers)
                val response = restTemplate.postForEntity(AUTH_URL, request, Map::class.java)

                if (response.statusCode.is2xxSuccessful && response.body != null) {
                    val responseBody = response.body!!
                    val code = responseBody["code"] as? Int
                    if (code == 0) {
                        @Suppress("UNCHECKED_CAST")
                        val dataMap = responseBody["data"] as? Map<String, Any>
                        val newToken = dataMap?.get("auth_token") as? String
                        // OPPO auth_token 預設 24 小時有效
                        if (newToken != null) {
                            authToken = newToken
                            tokenExpireAt = Instant.now().plusSeconds(86400L - TOKEN_REFRESH_BUFFER_SECONDS)
                            log.info("OPPO auth_token 刷新成功")
                            return@withLock newToken
                        }
                    }
                }

                log.error("OPPO auth_token 獲取失敗: status={}, body={}", response.statusCode, response.body)
                null
            } catch (e: Exception) {
                log.error("OPPO auth_token 獲取異常: err={}", e.message, e)
                null
            }
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
