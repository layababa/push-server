package com.layababateam.pushserver.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

// 内部发送消息请求（管理端 JWT 认证）
data class SendMessageRequest(
    @field:NotNull(message = "接收者ID不能为空")
    val receiverId: Long,
    
    val title: String? = null,
    
    @field:NotBlank(message = "消息内容不能为空")
    val content: String,
    
    val url: String? = null,
    
    val group: String? = null,
    
    val msgType: String = "notification",
    
    // 扩展数据，jsonb存
    val extraData: Map<String, Any>? = null
)

// Webhook 推送请求（POST /push/{pushKey} 的 JSON body）
data class WebhookPushRequest(
    val title: String? = null,
    
    val body: String? = null,
    
    val url: String? = null,
    
    val group: String? = null,
    
    val icon: String? = null
)

// 消息响应
data class MessageResponse(
    val id: Long,
    val senderId: Long?,
    val receiverId: Long,
    val title: String?,
    val content: String?,
    val url: String?,
    val group: String?,
    val msgType: String,
    val readStatus: Int,
    val createdAt: String,
    val readAt: String?
)

// 分页响应（含 hasMore 标记）
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasMore: Boolean = false
)

// Webhook 推送成功响应
data class WebhookPushResponse(
    val messageId: Long
)

// mq消息体，发到队列里的
data class PushMessagePayload(
    val messageId: Long,
    val receiverId: Long,
    val deviceUuids: List<String>,
    val title: String?,
    val content: String?,
    val extraData: Map<String, Any>?
)
