package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.service.MessageService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/messages")
class MessageController(
    private val msgService: MessageService
) {
    // 发送消息
    // senderId先写死，后面接jwt再从token里取
    @PostMapping
    fun sendMessage(
        @Valid @RequestBody req: SendMessageRequest,
        @RequestHeader("X-User-Id", required = false) senderId: Long?
    ): ApiResult<MessageResponse> {
        return try {
            val msg = msgService.sendMessage(senderId, req)
            ApiResult.ok(msg, "发送成功")
        } catch (e: Exception) {
            ApiResult.fail(e.message ?: "发送失败")
        }
    }
    
    // 获取消息列表（分页）
    @GetMapping
    fun getMessages(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResult<PageResponse<MessageResponse>> {
        // 限制一下size，防止一次拉太多
        val safeSize = minOf(size, 100)
        val result = msgService.getMessages(userId, page, safeSize)
        return ApiResult.ok(result)
    }
    
    // 标记单条已读
    @PostMapping("/{id}/read")
    fun markAsRead(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") userId: Long
    ): ApiResult<Boolean> {
        val success = msgService.markAsRead(id, userId)
        return if (success) {
            ApiResult.ok(true, "已标记为已读")
        } else {
            ApiResult.fail("标记失败，消息不存在或无权限", ApiResult.CODE_NOT_FOUND)
        }
    }
    
    // 全部标记已读
    @PostMapping("/read-all")
    fun markAllAsRead(@RequestHeader("X-User-Id") userId: Long): ApiResult<Int> {
        val count = msgService.markAllAsRead(userId)
        return ApiResult.ok(count, "已标记${count}条为已读")
    }
    
    // 获取未读数
    @GetMapping("/unread-count")
    fun getUnreadCount(@RequestHeader("X-User-Id") userId: Long): ApiResult<Long> {
        val count = msgService.getUnreadCount(userId)
        return ApiResult.ok(count)
    }
}
