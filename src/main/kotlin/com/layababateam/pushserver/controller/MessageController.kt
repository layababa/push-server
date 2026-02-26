package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.service.MessageService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/**
 * 消息管理 Controller — JWT 认证
 * 路径: /api/v1/messages
 */
@RestController
@RequestMapping("/api/v1/messages")
class MessageController(
    private val msgService: MessageService
) {
    // 内部发送消息（管理端）
    @PostMapping
    fun sendMessage(
        @Valid @RequestBody req: SendMessageRequest,
        request: HttpServletRequest
    ): ApiResult<MessageResponse> {
        val senderId = request.getAttribute("userId") as? Long
        return try {
            val msg = msgService.sendMessage(senderId, req)
            ApiResult.ok(msg, "发送成功")
        } catch (e: Exception) {
            ApiResult.fail(e.message ?: "发送失败")
        }
    }
    
    // 获取消息列表（分页 + 时间过滤）
    @GetMapping
    fun getMessages(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "24") hours: Int
    ): ApiResult<PageResponse<MessageResponse>> {
        val userId = request.getAttribute("userId") as? Long
            ?: return ApiResult.fail("未登录", ApiResult.CODE_UNAUTHORIZED)
        val safeSize = minOf(size, 100)
        val result = msgService.getMessages(userId, page, safeSize, hours)
        return ApiResult.ok(result)
    }
    
    // 标记单条已读
    @PostMapping("/{id}/read")
    fun markAsRead(
        @PathVariable id: Long,
        request: HttpServletRequest
    ): ApiResult<Boolean> {
        val userId = request.getAttribute("userId") as? Long
            ?: return ApiResult.fail("未登录", ApiResult.CODE_UNAUTHORIZED)
        val success = msgService.markAsRead(id, userId)
        return if (success) {
            ApiResult.ok(true, "已标记为已读")
        } else {
            ApiResult.fail("标记失败，消息不存在或无权限", ApiResult.CODE_NOT_FOUND)
        }
    }
    
    // 全部标记已读
    @PostMapping("/read-all")
    fun markAllAsRead(request: HttpServletRequest): ApiResult<Int> {
        val userId = request.getAttribute("userId") as? Long
            ?: return ApiResult.fail("未登录", ApiResult.CODE_UNAUTHORIZED)
        val count = msgService.markAllAsRead(userId)
        return ApiResult.ok(count, "已标记${count}条为已读")
    }
    
    // 获取未读数
    @GetMapping("/unread-count")
    fun getUnreadCount(request: HttpServletRequest): ApiResult<Long> {
        val userId = request.getAttribute("userId") as? Long
            ?: return ApiResult.fail("未登录", ApiResult.CODE_UNAUTHORIZED)
        val count = msgService.getUnreadCount(userId)
        return ApiResult.ok(count)
    }
    
    // 清空当前用户所有消息
    @PostMapping("/clear")
    fun clearMessages(request: HttpServletRequest): ApiResult<Int> {
        val userId = request.getAttribute("userId") as? Long
            ?: return ApiResult.fail("未登录", ApiResult.CODE_UNAUTHORIZED)
        val count = msgService.clearMessages(userId)
        return if (count >= 0) {
            ApiResult.ok(count, "已清空${count}条消息")
        } else {
            ApiResult.fail("清空消息失败，请稍后重试")
        }
    }
}
