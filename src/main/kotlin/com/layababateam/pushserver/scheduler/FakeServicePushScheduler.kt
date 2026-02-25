package com.layababateam.pushserver.scheduler

import com.layababateam.pushserver.repository.PublicServiceRepository
import com.layababateam.pushserver.repository.ServiceMessagePoolRepository
import com.layababateam.pushserver.service.ServiceBroadcastService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * 模拟服务推送 — 从消息池随机抽取，低频率随机间隔
 *
 * 阿里云监控: 30~90 分钟随机
 * 天气预警:   60~180 分钟随机
 * 足球进球:   15~60 分钟随机
 */
@Component
class FakeServicePushScheduler(
    private val serviceRepo: PublicServiceRepository,
    private val poolRepo: ServiceMessagePoolRepository,
    private val broadcastService: ServiceBroadcastService,
    private val taskScheduler: TaskScheduler
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        // 启动后延迟 1~3 分钟开始首次推送（错开三个服务）
        scheduleNext("srv_aliyun_status", 30 * 60L to 90 * 60L, Random.nextLong(60, 180))
        scheduleNext("srv_weather_alerts", 60 * 60L to 180 * 60L, Random.nextLong(120, 240))
        scheduleNext("srv_football_goals", 15 * 60L to 60 * 60L, Random.nextLong(30, 90))
        log.info("FakeServicePushScheduler 已启动，使用消息池 + 随机间隔调度")
    }

    /**
     * 调度下一次推送
     *
     * @param serviceCode  服务编码
     * @param intervalRange 下次推送的随机间隔范围（秒）
     * @param delaySec     本次延迟（秒）
     */
    private fun scheduleNext(serviceCode: String, intervalRange: Pair<Long, Long>, delaySec: Long) {
        taskScheduler.schedule(
            { pushFromPool(serviceCode, intervalRange) },
            Instant.now().plus(Duration.ofSeconds(delaySec))
        )
    }

    /**
     * 从消息池随机取一条消息，广播给该服务的所有订阅者
     */
    private fun pushFromPool(serviceCode: String, intervalRange: Pair<Long, Long>) {
        try {
            val service = serviceRepo.findByServiceCode(serviceCode)
            if (service == null) {
                log.warn("服务[{}]未找到，跳过推送", serviceCode)
                return
            }
            val serviceId = service.id ?: return

            val msg = poolRepo.findRandomByServiceCode(serviceCode)
            if (msg == null) {
                log.warn("消息池[{}]为空，跳过推送", serviceCode)
                return
            }

            broadcastService.broadcast(serviceId, service.name, msg.title, msg.body)
        } catch (e: Exception) {
            log.error("服务[{}]模拟推送异常: {}", serviceCode, e.message)
        } finally {
            // 调度下一次
            val nextDelay = Random.nextLong(intervalRange.first, intervalRange.second)
            log.debug("服务[{}]下次推送: {}分钟后", serviceCode, nextDelay / 60)
            scheduleNext(serviceCode, intervalRange, nextDelay)
        }
    }
}
