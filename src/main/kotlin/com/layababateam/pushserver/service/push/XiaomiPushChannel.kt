package com.layababateam.pushserver.service.push

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

/**
 * 小米 MiPush 推送通道 — 透過小米推送 HTTP API 發送通知
 *
 * API 文檔：https://dev.mi.com/console/doc/detail?pId=1163
 * 認證方式：Header Authorization: key={AppSecret}
 */
@Service
@ConditionalOnProperty("push.xiaomi.enabled", havingValue = "true", matchIfMissing = false)
class XiaomiPushChannel(
    @Value("\${push.xiaomi.app-secret}") private val appSecret: String,
    @Value("\${push.xiaomi.package-name}") private val packageName: String,
) : PushChannel {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    override val channelType: String = "xiaomi"

    companion object {
        private const val PUSH_URL = "https://api.xmpush.xiaomi.com/v3/message/regid"
    }

    override fun send(
        pushToken: String,
        title: String?,
        body: String?,
        data: Map<String, String>?,
    ): Boolean {
        if (pushToken.isBlank()) {
            log.warn("pushToken 為空，跳過小米推送")
            return false
        }

        if (appSecret.isBlank()) {
            log.error("小米推送 appSecret 未配置")
            return false
        }

        return try {
            val headers = HttpHeaders().apply {
                set("Authorization", "key=$appSecret")
                contentType = MediaType.APPLICATION_FORM_URLENCODED
            }

            val params = LinkedMultiValueMap<String, String>().apply {
                add("registration_id", pushToken)
                add("title", title ?: "新消息")
                add("description", body ?: "")
                add("restricted_package_name", packageName)
                add("notify_type", "-1") // 預設全部（聲音+震動+LED）
                add("pass_through", "0") // 0=通知欄消息, 1=透傳
                if (!data.isNullOrEmpty()) {
                    add("payload", data.entries.joinToString(",") { "${it.key}=${it.value}" })
                }
            }

            val request = HttpEntity(params, headers)
            val response = restTemplate.postForEntity(PUSH_URL, request, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                log.info("小米推送成功: token={}..., response={}", pushToken.take(20), response.body?.take(200))
                true
            } else {
                log.error("小米推送失敗: httpStatus={}, token={}...", response.statusCode, pushToken.take(20))
                false
            }
        } catch (e: Exception) {
            log.error("小米推送異常: token={}..., err={}", pushToken.take(20), e.message, e)
            false
        }
    }
}
