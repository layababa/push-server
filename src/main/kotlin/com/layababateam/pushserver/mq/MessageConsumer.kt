package com.layababateam.pushserver.mq

import com.layababateam.pushserver.dto.PushMessagePayload
import com.layababateam.pushserver.service.MessageService
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

@Component
class MessageConsumer(
    private val msgService: MessageService
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
                // TODO: 这里以后接真正的推送SDK (FCM/APNs/华为/小米...)
                // 现在先打个日志模拟一下
                log.info("已通过 MQ 接收到任务，准备向 UUID: {} 发送推送内容: {}", 
                    uuid, payload.content?.take(50))
                
                // 模拟推送操作
                simulatePush(uuid, payload)
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
            // 实际生产环境可能需要更复杂的重试策略
            channel.basicNack(deliveryTag, false, false)
        }
    }
    
    // 模拟推送，以后换成真实SDK
    private fun simulatePush(deviceUuid: String, payload: PushMessagePayload) {
        // 假装做了点事情
        log.debug("模拟推送到设备: {}, title={}", deviceUuid, payload.title)
        
        // 如果需要模拟延迟可以在这加
        // 但最好别用Thread.sleep，影响吞吐量
    }
}
