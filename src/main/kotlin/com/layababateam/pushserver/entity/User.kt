package com.layababateam.pushserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_username", columnList = "username", unique = true),
        Index(name = "idx_phone", columnList = "phone", unique = true),
        Index(name = "idx_email", columnList = "email", unique = true)
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
    
    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    // 更新时间戳
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
