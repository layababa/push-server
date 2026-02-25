package com.layababateam.pushserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_username", columnList = "username", unique = true),
        Index(name = "idx_phone", columnList = "phone", unique = true),
        Index(name = "idx_email", columnList = "email", unique = true),
        Index(name = "idx_push_key", columnList = "push_key", unique = true)
    ]
)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    @Column(nullable = false, length = 50)
    var username: String = "",
    
    @Column(nullable = false)
    var password: String = "",
    
    // 手机号可为空，但如果有值必须唯一
    @Column(length = 20)
    var phone: String? = null,
    
    // 邮箱同理
    @Column(length = 100)
    var email: String? = null,
    
    // 用户专属推送 Key，用于 webhook 推送鉴权
    // nullable=true 兼容已有数据，@PrePersist 保证新用户一定有值
    @Column(name = "push_key", length = 64, unique = true)
    var pushKey: String? = null,
    
    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun onCreate() {
        if (pushKey.isNullOrEmpty()) {
            pushKey = generatePushKey()
        }
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    /**
     * 确保 pushKey 存在（兼容老用户懒生成）
     */
    fun ensurePushKey(): String {
        if (pushKey.isNullOrEmpty()) {
            pushKey = generatePushKey()
        }
        return pushKey!!
    }

    companion object {
        fun generatePushKey(): String = "sec_${UUID.randomUUID().toString().replace("-", "")}"
    }
}
