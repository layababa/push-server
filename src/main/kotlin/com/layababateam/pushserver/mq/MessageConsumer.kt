package com.layababateam.pushserver.mq

import com.layababateam.pushserver.dto.PushMessagePayload
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.service.FcmPushService
import com.layababateam.pushserver.service.MessageService
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

@Component
class MessageConsumer(
    private val msgService: MessageService,
    private val fcmPushService: FcmPushService,
    private val deviceRepo: DeviceBindingRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    @RabbitListener(queues = ["\${mq.queue.push}"])
    fun handlePushTask(
        payload: PushMessagePayload,
        channel: Channel,
        @Header(AmqpHeaders.DELIVERY_TAG) deliveryTag: Long
    ) {
        try {
            log.info("收到推送任务: msgId={}, receiver={}", payload.messageId, payload.receiverId)
            
            // 遍历设备uuid执行推送
            for (uuid in payload.deviceUuids) {
                val binding = deviceRepo.findByDeviceUuid(uuid).orElse(null)
                val pushToken = binding?.pushToken
                
                if (pushToken.isNullOrBlank()) {
                    log.warn("设备 {} 无 pushToken，跳过 FCM 推送", uuid)
                    continue
                }
                
                // 构建额外数据
                val data = mutableMapOf(
                    "messageId" to payload.messageId.toString(),
                    "receiverId" to payload.receiverId.toString()
                )
                payload.extraData?.forEach { (k, v) -> data[k] = v.toString() }
                
                // 通过 FCM 发送真实推送
                fcmPushService.sendPush(
                    pushToken = pushToken,
                    title = payload.title,
                    body = payload.content,
                    data = data
                )
            }
            
            // 更新推送状态为已发送
            msgService.updatePushStatus(payload.messageId, 1)
            
            // 手动ack
            channel.basicAck(deliveryTag, false)
            log.debug("push task done, msgId={}", payload.messageId)
            
        } catch (e: Exception) {
            log.error("push task failed: msgId={}, err={}", payload.messageId, e.message, e)
            
            // 更新状态为失败
            try {
                msgService.updatePushStatus(payload.messageId, 2)
            } catch (ex: Exception) {
                log.error("update push status failed", ex)
            }
            
            // nack，不重新入队（避免死循环）
            channel.basicNack(deliveryTag, false, false)
        }
    }
}
