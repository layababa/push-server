package com.layababateam.pushserver.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

// 发送消息请求
data class SendMessageRequest(
    @field:NotNull(message = "接收者ID不能为空")
    val receiverId: Long,
    
    val title: String? = null,
    
    @field:NotBlank(message = "消息内容不能为空")
    val content: String,
    
    val msgType: String = "notification",
    
    // 扩展数据，jsonb存
    val extraData: Map<String, Any>? = null
)

// 消息响应
data class MessageResponse(
    val id: Long,
    val senderId: Long?,
    val receiverId: Long,
    val title: String?,
    val content: String?,
    val msgType: String,
    val extraData: Map<String, Any>?,
    val readStatus: Int,
    val createdAt: String,
    val readAt: String?
)

// 分页响应
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
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
