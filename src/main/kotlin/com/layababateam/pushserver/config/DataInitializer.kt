package com.layababateam.pushserver.config

import com.layababateam.pushserver.entity.PublicService
import com.layababateam.pushserver.repository.PublicServiceRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 应用启动时插入种子数据（公共服务）
 * 仅在数据库中无对应 serviceCode 时才插入，避免重复
 */
@Component
class DataInitializer(
    private val serviceRepo: PublicServiceRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val seeds = listOf(
            PublicService(
                serviceCode = "srv_aliyun_status",
                name = "阿里云可用性监控",
                description = "监控阿里云各可用区的异常状态并推送报警",
                iconUrl = "https://img.alicdn.com/tfs/TB1_ZXuNcfpK1RjSZFOXXa6nFXa-32-32.ico"
            ),
            PublicService(
                serviceCode = "srv_weather_alerts",
                name = "极端天气预警",
                description = "所在城市的极端天气红色预警推送",
                iconUrl = "https://cdn-icons-png.flaticon.com/512/1779/1779940.png"
            ),
            PublicService(
                serviceCode = "srv_football_goals",
                name = "热门比赛进球提醒",
                description = "五大联赛重要比赛实时进球通知",
                iconUrl = "https://cdn-icons-png.flaticon.com/512/861/861512.png"
            )
        )

        var inserted = 0
        for (seed in seeds) {
            val existing = serviceRepo.findByServiceCode(seed.serviceCode)
            if (existing == null) {
                serviceRepo.save(seed)
                inserted++
                log.info("Seed service inserted: {} ({})", seed.name, seed.serviceCode)
            }
        }

        if (inserted > 0) {
            log.info("DataInitializer: inserted {} seed services", inserted)
        } else {
            log.info("DataInitializer: all seed services already exist, skipped")
        }
    }
}
