package com.layababateam.pushserver.service

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.entity.AppMessage
import com.layababateam.pushserver.mq.MessageProducer
import com.layababateam.pushserver.repository.AppMessageRepository
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class MessageService(
    private val msgRepo: AppMessageRepository,
    private val deviceRepo: DeviceBindingRepository,
    private val userRepo: UserRepository,
    private val producer: MessageProducer
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 内部发送消息（管理端 JWT 认证）
     */
    @Transactional
    fun sendMessage(senderId: Long?, req: SendMessageRequest): MessageResponse {
        val msg = AppMessage(
            senderId = senderId,
            receiverId = req.receiverId,
            title = req.title,
            content = req.content,
            url = req.url,
            group = req.group,
            msgType = req.msgType,
            extraData = req.extraData,
            pushStatus = 0
        )
        val saved = msgRepo.save(msg)
        log.debug("msg saved: id={}, receiver={}", saved.id, saved.receiverId)
        
        dispatchPush(saved)
        
        return toResponse(saved)
    }
    
    /**
     * Webhook 推送（pushKey 认证，外部脚本调用）
     */
    @Transactional
    fun pushViaWebhook(pushKey: String, title: String?, body: String?, url: String?, group: String?): WebhookPushResponse {
        require(pushKey.isNotBlank()) { "MessageService.pushViaWebhook: pushKey 不能为空" }
        
        val user = userRepo.findByPushKey(pushKey).orElse(null)
            ?: throw IllegalArgumentException("无效的 pushKey")
        
        val userId = user.id ?: throw IllegalStateException("用户 ID 为空")
        
        val msg = AppMessage(
            senderId = null,
            receiverId = userId,
            title = title,
            content = body,
            url = url,
            group = group,
            msgType = "notification",
            pushStatus = 0
        )
        val saved = msgRepo.save(msg)
        log.info("webhook push saved: id={}, receiver={}, pushKey={}", saved.id, userId, pushKey.take(12) + "***")
        
        dispatchPush(saved)
        
        return WebhookPushResponse(messageId = saved.id ?: 0)
    }
    
    /**
     * 获取消息列表（分页 + 时间过滤）
     * @param hours 0 表示不过滤，>0 表示只查 N 小时内的消息
     */
    fun getMessages(userId: Long, page: Int, size: Int, hours: Int = 24): PageResponse<MessageResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        
        val pageResult = if (hours > 0) {
            val since = LocalDateTime.now().minusHours(hours.toLong())
            msgRepo.findByReceiverIdAndCreatedAtAfter(userId, since, pageable)
        } else {
            msgRepo.findByReceiverIdOrderByCreatedAtDesc(userId, pageable)
        }
        
        return PageResponse(
            content = pageResult.content.map { toResponse(it) },
            page = page,
            size = size,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages,
            hasMore = pageResult.hasNext()
        )
    }
    
    @Transactional
    fun markAsRead(msgId: Long, userId: Long): Boolean {
        val msg = msgRepo.findById(msgId).orElse(null) ?: return false
        
        if (msg.receiverId != userId) {
            log.warn("user {} try to read msg {} but not owner", userId, msgId)
            return false
        }
        
        return msgRepo.markAsRead(msgId) > 0
    }
    
    @Transactional
    fun markAllAsRead(userId: Long): Int {
        return msgRepo.markAllAsRead(userId)
    }
    
    fun getUnreadCount(userId: Long): Long {
        return msgRepo.countByReceiverIdAndReadStatus(userId, 0)
    }
    
    /**
     * 清空用户所有消息（物理删除）
     * @return 被删除的消息数量，失败时返回 -1
     */
    @Transactional
    fun clearMessages(userId: Long): Int {
        return try {
            val count = msgRepo.deleteByReceiverId(userId)
            log.info("user {} cleared {} messages", userId, count)
            count
        } catch (e: Exception) {
            log.error("clear messages failed, userId={}", userId, e)
            -1
        }
    }
    
    // 更新推送状态，consumer调用
    @Transactional
    fun updatePushStatus(msgId: Long, status: Int) {
        msgRepo.updatePushStatus(msgId, status)
    }
    
    /**
     * 分发推送任务到 MQ
     */
    private fun dispatchPush(saved: AppMessage) {
        val deviceUuids = deviceRepo.findDeviceUuidsByUserId(saved.receiverId)
        
        if (deviceUuids.isNotEmpty()) {
            val payload = PushMessagePayload(
                messageId = saved.id ?: return,
                receiverId = saved.receiverId,
                deviceUuids = deviceUuids,
                title = saved.title,
                content = saved.content,
                extraData = saved.extraData
            )
            producer.sendPushTask(payload)
        } else {
            log.warn("receiver {} has no device bound, skip push", saved.receiverId)
        }
    }
    
    private fun toResponse(msg: AppMessage): MessageResponse {
        return MessageResponse(
            id = msg.id ?: 0,
            senderId = msg.senderId,
            receiverId = msg.receiverId,
            title = msg.title,
            content = msg.content,
            url = msg.url,
            group = msg.group,
            msgType = msg.msgType,
            readStatus = msg.readStatus,
            createdAt = msg.createdAt.format(dtFmt),
            readAt = msg.readAt?.format(dtFmt)
        )
    }
}
