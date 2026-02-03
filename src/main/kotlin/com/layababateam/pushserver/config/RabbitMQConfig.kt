package com.layababateam.pushserver.config

import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory

@Configuration
class RabbitMQConfig {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    @Value("\${mq.exchange.message}")
    private lateinit var exchangeName: String
    
    @Value("\${mq.queue.push}")
    private lateinit var queueName: String
    
    @Value("\${mq.routing-key.push}")
    private lateinit var routingKey: String
    
    // direct exchange，比较简单直接
    @Bean
    fun messageExchange(): DirectExchange {
        return DirectExchange(exchangeName, true, false)
    }
    
    @Bean
    fun pushQueue(): Queue {
        // durable=true 持久化队列
        return QueueBuilder.durable(queueName).build()
    }
    
    // 绑定queue到exchange
    @Bean
    fun pushBinding(pushQueue: Queue, messageExchange: DirectExchange): Binding {
        return BindingBuilder.bind(pushQueue)
            .to(messageExchange)
            .with(routingKey)
    }
    
    // json序列化
    @Bean
    fun jackson2JsonMessageConverter(): Jackson2JsonMessageConverter {
        return Jackson2JsonMessageConverter()
    }
    
    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        converter: Jackson2JsonMessageConverter
    ): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = converter
        
        // confirm回调 - 消息到达exchange的确认
        template.setConfirmCallback { correlationData, ack, cause ->
            if (ack) {
                log.debug("msg confirm ok, id={}", correlationData?.id)
            } else {
                log.warn("msg confirm fail, id={}, cause={}", correlationData?.id, cause)
            }
        }
        
        // return回调 - 消息无法路由到queue时触发
        template.setReturnsCallback { returned ->
            log.warn(
                "msg return back: exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.exchange, returned.routingKey, 
                returned.replyCode, returned.replyText
            )
        }
        
        return template
    }
}
