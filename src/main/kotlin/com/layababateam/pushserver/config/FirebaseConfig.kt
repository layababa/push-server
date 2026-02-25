package com.layababateam.pushserver.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import jakarta.annotation.PostConstruct

@Configuration
class FirebaseConfig(
    private val resourceLoader: ResourceLoader,
    @Value("\${fcm.credentials-path:}") private val credentialsPath: String,
    @Value("\${fcm.enabled:false}") private val enabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        if (!enabled) {
            log.warn("FCM 推送已禁用 (fcm.enabled=false)")
            return
        }

        if (credentialsPath.isBlank()) {
            log.warn("FCM credentials-path 未配置，跳过 Firebase 初始化")
            return
        }

        if (FirebaseApp.getApps().isNotEmpty()) {
            log.info("FirebaseApp 已初始化，跳过重复初始化")
            return
        }

        try {
            val resource = resourceLoader.getResource(credentialsPath)
            val credentials = resource.inputStream.use { GoogleCredentials.fromStream(it) }

            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()

            FirebaseApp.initializeApp(options)
            log.info("Firebase Admin SDK 初始化成功")
        } catch (e: Exception) {
            log.error("Firebase Admin SDK 初始化失败: {}", e.message, e)
        }
    }
}
