package com.layababateam.pushserver.service.auth

import com.aliyun.auth.credentials.Credential
import com.aliyun.auth.credentials.provider.StaticCredentialProvider
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest
import darabonba.core.client.ClientOverrideConfiguration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * 阿里云短信发送服务（短信服务 Dysms）
 * 职责单一：接收手机号 + 验证码 → 调阿里云 SendSms API → 返回成功/失败
 */
@Service
class SmsService(
    @Value("\${sms.aliyun.access-key-id:}") private val accessKeyId: String,
    @Value("\${sms.aliyun.access-key-secret:}") private val accessKeySecret: String,
    @Value("\${sms.aliyun.region:cn-hangzhou}") private val region: String,
    @Value("\${sms.aliyun.endpoint:dysmsapi.aliyuncs.com}") private val endpoint: String,
    @Value("\${sms.aliyun.sign-name:信推达}") private val signName: String,
    @Value("\${sms.aliyun.template-code:100001}") private val templateCode: String,
    @Value("\${otp.expire-seconds:300}") private val expireSeconds: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var client: AsyncClient? = null

    @PostConstruct
    fun init() {
        if (accessKeyId.isBlank() || accessKeySecret.isBlank()
            || accessKeyId == "your-access-key-id"
        ) {
            log.warn("SmsService: 阿里云 AccessKey 未配置，短信发送不可用")
            return
        }

        try {
            val provider = StaticCredentialProvider.create(
                Credential.builder()
                    .accessKeyId(accessKeyId)
                    .accessKeySecret(accessKeySecret)
                    .build()
            )

            client = AsyncClient.builder()
                .region(region)
                .credentialsProvider(provider)
                .overrideConfiguration(
                    ClientOverrideConfiguration.create()
                        .setEndpointOverride(endpoint)
                )
                .build()

            log.info("SmsService: 阿里云短信客户端初始化成功, region={}", region)
        } catch (e: Exception) {
            log.error("SmsService: 阿里云短信客户端初始化失败", e)
        }
    }

    @PreDestroy
    fun destroy() {
        try {
            client?.close()
        } catch (e: Exception) {
            log.warn("SmsService: 关闭阿里云短信客户端异常", e)
        }
    }

    /**
     * 发送短信验证码
     * @param phone 目标手机号
     * @param code 验证码
     * @return true=发送成功, false=发送失败
     */
    fun send(phone: String, code: String): Boolean {
        val aliyunClient = client
        if (aliyunClient == null) {
            log.error("SmsService.send: 阿里云短信客户端未初始化，无法发送短信到 {}", phone)
            return false
        }

        require(phone.isNotBlank()) { "SmsService.send: phone 不能为空" }
        require(code.isNotBlank()) { "SmsService.send: code 不能为空" }

        val expireMinutes = (expireSeconds / 60).coerceAtLeast(1)
        val templateParam = """{"code":"$code","min":"$expireMinutes"}"""

        val request = SendSmsRequest.builder()
            .signName(signName)
            .templateCode(templateCode)
            .templateParam(templateParam)
            .phoneNumbers(phone)
            .build()

        return try {
            val response = aliyunClient.sendSms(request).get()
            val body = response.body
            if (body != null && "OK" == body.code) {
                log.info("SmsService: 短信发送成功, phone={}", phone)
                true
            } else {
                log.error(
                    "SmsService: 短信发送失败, phone={}, code={}, message={}",
                    phone, body?.code, body?.message
                )
                false
            }
        } catch (e: Exception) {
            log.error("SmsService: 短信发送异常, phone={}", phone, e)
            false
        }
    }
}
