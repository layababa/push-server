package com.layababateam.pushserver.service

import com.layababateam.pushserver.dto.*
import com.layababateam.pushserver.entity.AppMessage
import com.layababateam.pushserver.mq.MessageProducer
import com.layababateam.pushserver.repository.AppMessageRepository
import com.layababateam.pushserver.repository.DeviceBindingRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class MessageService(
    private val msgRepo: AppMessageRepository,
    private val deviceRepo: DeviceBindingRepository,
    private val producer: MessageProducer
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    @Transactional
    fun sendMessage(senderId: Long?, req: SendMessageRequest): MessageResponse {
        // 先存库
        val msg = AppMessage(
            senderId = senderId,
            receiverId = req.receiverId,
            title = req.title,
            content = req.content,
            msgType = req.msgType,
            extraData = req.extraData,
            pushStatus = 0  // pending
        )
        val saved = msgRepo.save(msg)
        log.debug("msg saved: id={}, receiver={}", saved.id, saved.receiverId)
        
        // 查接收者的设备列表
        val deviceUuids = deviceRepo.findDeviceUuidsByUserId(req.receiverId)
        
        if (deviceUuids.isNotEmpty()) {
            // 发到mq
            val payload = PushMessagePayload(
                messageId = saved.id!!,
                receiverId = saved.receiverId,
                deviceUuids = deviceUuids,
                title = saved.title,
                content = saved.content,
                extraData = saved.extraData
            )
            producer.sendPushTask(payload)
        } else {
            log.warn("receiver {} has no device bound, skip push", req.receiverId)
        }
        
        return toResponse(saved)
    }
    
    fun getMessages(userId: Long, page: Int, size: Int): PageResponse<MessageResponse> {
        // 分页查询，按创建时间倒序
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pageResult = msgRepo.findByReceiverIdOrderByCreatedAtDesc(userId, pageable)
        
        return PageResponse(
            content = pageResult.content.map { toResponse(it) },
            page = page,
            size = size,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }
    
    @Transactional
    fun markAsRead(msgId: Long, userId: Long): Boolean {
        // 先查下这消息是不是这个用户的
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
    
    // 更新推送状态，consumer调用
    @Transactional
    fun updatePushStatus(msgId: Long, status: Int) {
        msgRepo.updatePushStatus(msgId, status)
    }
    
    private fun toResponse(msg: AppMessage): MessageResponse {
        return MessageResponse(
            id = msg.id!!,
            senderId = msg.senderId,
            receiverId = msg.receiverId,
            title = msg.title,
            content = msg.content,
            msgType = msg.msgType,
            extraData = msg.extraData,
            readStatus = msg.readStatus,
            createdAt = msg.createdAt.format(dtFmt),
            readAt = msg.readAt?.format(dtFmt)
        )
    }
}
