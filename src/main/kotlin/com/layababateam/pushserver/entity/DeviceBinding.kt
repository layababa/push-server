package com.layababateam.pushserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "device_binding",
    indexes = [
        Index(name = "idx_device_uuid", columnList = "device_uuid", unique = true),
        Index(name = "idx_user_id", columnList = "user_id")
    ]
)
class DeviceBinding(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    @Column(name = "device_uuid", nullable = false, length = 64)
    var deviceUuid: String = "",
    
    @Column(name = "user_id")
    var userId: Long? = null,
    
    // 设备类型 android/ios/web啥的
    @Column(name = "device_type", length = 20)
    var deviceType: String? = null,
    
    // 推送token，以后接fcm/apns用
    @Column(name = "push_token", length = 255)
    var pushToken: String? = null,
    
    @Column(name = "last_login_ip", length = 50)
    var lastLoginIp: String? = null,
    
    @Column(name = "last_active")
    var lastActive: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        lastActive = LocalDateTime.now()
    }
}
