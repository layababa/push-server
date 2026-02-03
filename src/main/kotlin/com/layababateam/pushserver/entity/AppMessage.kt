package com.layababateam.pushserver.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(
    name = "app_message",
    indexes = [
        // 复合索引: receiver + created_at，查消息列表用
        Index(name = "idx_receiver_created", columnList = "receiver_id, created_at DESC"),
        Index(name = "idx_sender", columnList = "sender_id")
    ]
)
class AppMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    @Column(name = "sender_id")
    var senderId: Long? = null,
    
    @Column(name = "receiver_id", nullable = false)
    var receiverId: Long = 0,
    
    // 消息标题
    @Column(length = 200)
    var title: String? = null,
    
    // 消息内容，用TEXT存，够大
    @Column(columnDefinition = "TEXT")
    var content: String? = null,
    
    // jsonb扩展字段，放一些额外信息
    // 比如 {"type": "order", "order_id": 123, "action": "shipped"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_data", columnDefinition = "jsonb")
    var extraData: Map<String, Any>? = null,
    
    // 消息类型 system/chat/notification...
    @Column(name = "msg_type", length = 20)
    var msgType: String = "notification",
    
    // 0=未读 1=已读
    @Column(name = "read_status")
    var readStatus: Int = 0,
    
    // 推送状态 0=pending 1=sent 2=failed
    @Column(name = "push_status")
    var pushStatus: Int = 0,
    
    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "read_at")
    var readAt: LocalDateTime? = null
)
