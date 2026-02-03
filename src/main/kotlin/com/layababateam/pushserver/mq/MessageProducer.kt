package com.layababateam.pushserver.mq

import com.layababateam.pushserver.dto.PushMessagePayload
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MessageProducer(
    private val rabbitTemplate: RabbitTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    @Value("\${mq.exchange.message}")
    private lateinit var exchange: String
    
    @Value("\${mq.routing-key.push}")
    private lateinit var routingKey: String
    
    fun sendPushTask(payload: PushMessagePayload) {
        // 生成correlationId用于追踪
        val corrId = UUID.randomUUID().toString()
        val correlationData = CorrelationData(corrId)
        
        log.info("sending push task to mq: msgId={}, receiver={}, devices={}", 
            payload.messageId, payload.receiverId, payload.deviceUuids.size)
        
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData)
    }
}
