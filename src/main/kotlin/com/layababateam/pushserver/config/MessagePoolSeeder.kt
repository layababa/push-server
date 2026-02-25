package com.layababateam.pushserver.config

import com.layababateam.pushserver.entity.ServiceMessagePool
import com.layababateam.pushserver.repository.ServiceMessagePoolRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 启动时预生成消息池 — 每个服务 500 条随机消息
 *
 * 仅在 service_message_pool 表为空时执行（幂等）。
 * 生成后由 FakeServicePushScheduler 随机抽取推送。
 */
@Component
class MessagePoolSeeder(
    private val poolRepo: ServiceMessagePoolRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MESSAGES_PER_SERVICE = 500
    }

    override fun run(args: ApplicationArguments) {
        seedIfEmpty("srv_aliyun_status", ::generateAliyunMessages)
        seedIfEmpty("srv_weather_alerts", ::generateWeatherMessages)
        seedIfEmpty("srv_football_goals", ::generateFootballMessages)
    }

    private fun seedIfEmpty(serviceCode: String, generator: () -> List<ServiceMessagePool>) {
        val existing = poolRepo.countByServiceCode(serviceCode)
        if (existing >= MESSAGES_PER_SERVICE) {
            log.info("消息池[{}] 已有 {} 条，跳过生成", serviceCode, existing)
            return
        }

        log.info("消息池[{}] 开始生成 {} 条消息...", serviceCode, MESSAGES_PER_SERVICE)
        val messages = generator()
        poolRepo.saveAll(messages)
        log.info("消息池[{}] 生成完成，共 {} 条", serviceCode, messages.size)
    }

    // ============================================================
    // 阿里云监控消息生成
    // ============================================================

    private val aliyunRegions = listOf(
        "cn-hangzhou" to "华东1（杭州）",
        "cn-shanghai" to "华东2（上海）",
        "cn-beijing" to "华北2（北京）",
        "cn-shenzhen" to "华南1（深圳）",
        "cn-hongkong" to "中国香港",
        "ap-southeast-1" to "新加坡",
        "us-west-1" to "美国（硅谷）",
        "eu-central-1" to "德国（法兰克福）"
    )

    private val aliyunServices = listOf("ECS", "RDS", "OSS", "SLB", "CDN", "Redis", "NAS", "ACK")

    private data class AliyunEvent(val type: String, val bodyBuilder: (String, String) -> String)

    private val aliyunEvents: List<AliyunEvent> = listOf(
        AliyunEvent("性能降级") { _, _ ->
            "部分实例响应延迟升高，P99 延迟从 ${Random.nextInt(5, 30)}ms 升至 ${Random.nextInt(200, 2000)}ms"
        },
        AliyunEvent("服务恢复") { _, _ ->
            "服务已恢复正常，故障持续 ${Random.nextInt(2, 45)} 分钟，影响实例 ${Random.nextInt(3, 50)} 台"
        },
        AliyunEvent("网络抖动") { _, _ ->
            "检测到跨可用区网络丢包率 ${"%.1f".format(Random.nextFloat() * 5 + 0.5)}%，正在自动切换链路"
        },
        AliyunEvent("磁盘 IO 告警") { _, _ ->
            "系统盘 IOPS 达到上限 ${Random.nextInt(10000, 50000)}，建议升级至 ESSD PL2"
        },
        AliyunEvent("实例自动重启") { _, _ ->
            "${Random.nextInt(1, 8)} 台实例因 OOM 触发自动重启，当前内存使用率 ${Random.nextInt(85, 99)}%"
        },
        AliyunEvent("SSL 证书即将过期") { regionCode, _ ->
            "域名 *.${regionCode.replace("-", "")}.com 的证书将于 ${Random.nextInt(3, 30)} 天后过期，请及时续签"
        },
        AliyunEvent("弹性扩容完成") { _, _ ->
            "集群已自动扩容 ${Random.nextInt(2, 10)} 个节点，当前 CPU 使用率已降至 ${Random.nextInt(35, 65)}%"
        },
        AliyunEvent("CPU 使用率告警") { _, svc ->
            "$svc 实例 CPU 使用率持续超过 ${Random.nextInt(85, 99)}%，已达 ${Random.nextInt(10, 60)} 分钟"
        },
        AliyunEvent("连接数耗尽") { _, svc ->
            "$svc 实例连接数已达上限 ${Random.nextInt(1000, 10000)}，当前排队 ${Random.nextInt(50, 500)} 个请求"
        },
        AliyunEvent("安全组规则变更") { _, _ ->
            "安全组 sg-${Random.nextInt(100000, 999999)} 新增 ${Random.nextInt(1, 5)} 条入方向规则，请确认是否为授权操作"
        },
        AliyunEvent("带宽突增告警") { _, _ ->
            "出方向带宽突增至 ${Random.nextInt(500, 5000)}Mbps，疑似异常流量，建议检查"
        },
        AliyunEvent("数据库慢查询") { _, _ ->
            "过去 ${Random.nextInt(5, 30)} 分钟内检测到 ${Random.nextInt(10, 200)} 条慢查询，最长耗时 ${Random.nextInt(5, 120)}s"
        }
    )

    private fun generateAliyunMessages(): List<ServiceMessagePool> {
        val messages = mutableListOf<ServiceMessagePool>()
        repeat(MESSAGES_PER_SERVICE) {
            val (regionCode, regionName) = aliyunRegions.random()
            val svc = aliyunServices.random()
            val event = aliyunEvents.random()

            messages.add(ServiceMessagePool().apply {
                serviceCode = "srv_aliyun_status"
                title = "[$svc] ${event.type} — $regionName"
                body = event.bodyBuilder(regionCode, svc)
            })
        }
        return messages
    }

    // ============================================================
    // 天气预警消息生成
    // ============================================================

    private val cities = listOf(
        "北京", "上海", "广州", "深圳", "成都", "杭州", "武汉", "南京",
        "重庆", "天津", "长沙", "西安", "苏州", "郑州", "青岛", "大连",
        "东京", "首尔", "曼谷", "新加坡", "吉隆坡", "河内", "马尼拉"
    )

    private data class WeatherEvent(
        val type: String,
        val color: String,
        val bodyBuilders: List<(String) -> String>
    )

    private val typhoonNames = listOf("杜苏芮", "苏拉", "海葵", "小犬", "三巴", "格美", "潭美", "摩羯", "贝碧嘉", "康妮")

    private val weatherEvents: List<WeatherEvent> = listOf(
        WeatherEvent("暴雨", "红色", listOf(
            { _ -> "预计未来3小时降雨量将达100mm以上，请注意防范城市内涝" },
            { _ -> "强降雨持续，累计降雨量已达${Random.nextInt(80, 250)}mm，低洼地区注意转移" },
            { _ -> "暴雨叠加上游泄洪，河道水位上涨${"%.1f".format(Random.nextFloat() * 3 + 1)}m，沿岸居民注意安全" },
            { _ -> "短时强降雨来袭，小时雨强${Random.nextInt(40, 80)}mm，注意山洪地质灾害" }
        )),
        WeatherEvent("台风", "橙色", listOf(
            { _ -> "第${Random.nextInt(5, 25)}号台风\"${typhoonNames.random()}\"已加强为超强台风，中心风力${Random.nextInt(14, 17)}级，预计${Random.nextInt(12, 48)}小时后登陆" },
            { _ -> "台风外围云系影响，沿海阵风达${Random.nextInt(10, 14)}级，海上作业船只请回港避风" },
            { city -> "台风路径西偏，预计在${city}沿海登陆，请提前做好防风准备" },
            { _ -> "台风\"${typhoonNames.random()}\"中心气压降至${Random.nextInt(920, 960)}hPa，风速${Random.nextInt(45, 70)}m/s" }
        )),
        WeatherEvent("高温", "红色", listOf(
            { _ -> "最高气温将达${Random.nextInt(38, 43)}°C，连续第${Random.nextInt(3, 15)}天高温，请注意防暑降温" },
            { _ -> "地表温度超过${Random.nextInt(55, 72)}°C，户外作业人员请做好防护措施" },
            { _ -> "高温叠加高湿，体感温度超${Random.nextInt(42, 50)}°C，老人儿童避免外出" },
            { _ -> "高温热浪持续，电网负荷创新高达${Random.nextInt(2000, 4000)}万千瓦，请节约用电" }
        )),
        WeatherEvent("寒潮", "橙色", listOf(
            { _ -> "48小时内气温将下降${Random.nextInt(8, 18)}°C以上，最低温降至-${Random.nextInt(5, 20)}°C" },
            { _ -> "强冷空气来袭，伴有${Random.nextInt(6, 9)}级偏北风，注意添衣保暖" },
            { _ -> "寒潮导致道路结冰，高速公路${Random.nextInt(5, 30)}个路段临时管控" },
            { _ -> "寒潮蓝色预警升级，农业设施注意防冻，供暖系统请检查运行状态" }
        )),
        WeatherEvent("大雾", "红色", listOf(
            { _ -> "能见度不足${Random.nextInt(30, 200)}m，高速公路${Random.nextInt(10, 40)}个收费站临时封闭" },
            { _ -> "辐射雾持续加重，预计上午${Random.nextInt(9, 11)}时后逐渐消散" },
            { _ -> "大雾导致航班延误${Random.nextInt(50, 300)}架次，旅客请及时查询航班动态" }
        )),
        WeatherEvent("雷暴", "黄色", listOf(
            { _ -> "强雷暴云团正在靠近，预计${Random.nextInt(10, 40)}分钟后影响市区，伴有短时大风" },
            { _ -> "已检测到地闪${Random.nextInt(50, 500)}次/小时，请远离空旷地带和高大树木" },
            { _ -> "雷暴大风预计阵风${Random.nextInt(8, 12)}级，注意户外广告牌和临时搭建物安全" }
        )),
        WeatherEvent("沙尘暴", "橙色", listOf(
            { _ -> "沙尘暴来袭，PM10浓度${Random.nextInt(500, 2000)}μg/m³，请关闭门窗减少外出" },
            { _ -> "能见度降至${Random.nextInt(100, 500)}m以下，空气质量严重污染，请佩戴防护口罩" }
        )),
        WeatherEvent("暴雪", "橙色", listOf(
            { _ -> "预计${Random.nextInt(6, 24)}小时内降雪量达${Random.nextInt(10, 30)}mm，道路积雪严重请注意出行安全" },
            { _ -> "暴雪导致${Random.nextInt(3, 15)}条公交线路停运，地铁正常运行" }
        ))
    )

    private fun generateWeatherMessages(): List<ServiceMessagePool> {
        val messages = mutableListOf<ServiceMessagePool>()
        repeat(MESSAGES_PER_SERVICE) {
            val city = cities.random()
            val event = weatherEvents.random()
            val bodyBuilder = event.bodyBuilders.random()

            messages.add(ServiceMessagePool().apply {
                serviceCode = "srv_weather_alerts"
                title = "⚠ ${city}${event.type}${event.color}预警"
                body = bodyBuilder(city)
            })
        }
        return messages
    }

    // ============================================================
    // 足球进球消息生成
    // ============================================================

    private data class MatchInfo(val league: String, val home: String, val away: String)

    private val matches = listOf(
        MatchInfo("英超", "曼城", "阿森纳"),
        MatchInfo("英超", "利物浦", "曼联"),
        MatchInfo("英超", "切尔西", "热刺"),
        MatchInfo("英超", "纽卡斯尔", "阿斯顿维拉"),
        MatchInfo("英超", "布莱顿", "西汉姆联"),
        MatchInfo("西甲", "皇家马德里", "巴塞罗那"),
        MatchInfo("西甲", "马德里竞技", "塞维利亚"),
        MatchInfo("西甲", "皇家社会", "比利亚雷亚尔"),
        MatchInfo("意甲", "国际米兰", "AC米兰"),
        MatchInfo("意甲", "那不勒斯", "尤文图斯"),
        MatchInfo("意甲", "罗马", "拉齐奥"),
        MatchInfo("意甲", "亚特兰大", "佛罗伦萨"),
        MatchInfo("德甲", "拜仁慕尼黑", "多特蒙德"),
        MatchInfo("德甲", "勒沃库森", "RB莱比锡"),
        MatchInfo("德甲", "斯图加特", "法兰克福"),
        MatchInfo("法甲", "巴黎圣日耳曼", "马赛"),
        MatchInfo("法甲", "摩纳哥", "里昂"),
        MatchInfo("欧冠", "皇马", "曼城"),
        MatchInfo("欧冠", "巴萨", "拜仁"),
        MatchInfo("欧冠", "利物浦", "国际米兰"),
        MatchInfo("欧冠", "多特蒙德", "巴黎"),
        MatchInfo("中超", "上海海港", "山东泰山"),
        MatchInfo("中超", "北京国安", "广州队"),
        MatchInfo("亚冠", "浦和红钻", "利雅得新月")
    )

    private val scorers = listOf(
        "哈兰德", "萨拉赫", "姆巴佩", "维尼修斯", "贝林厄姆",
        "凯恩", "劳塔罗", "莱万", "奥斯梅恩", "穆西亚拉",
        "帕尔默", "孙兴慜", "福登", "格列兹曼", "恩昆库",
        "萨卡", "拉什福德", "努涅斯", "迪亚斯", "罗德里戈",
        "久保建英", "武磊", "伊东纯也", "黄喜灿", "金玟哉"
    )

    private val goalTypes = listOf(
        "右脚劲射破门", "头球攻门得分", "禁区外远射直挂死角", "点球命中",
        "反击中单刀赴会冷静推射", "角球混战中捅射入网",
        "任意球直接破门", "凌空抽射世界波", "VAR确认进球有效",
        "补时绝杀！", "梅开二度！", "帽子戏法！",
        "胸部停球后转身抽射", "人球分过晃开门将轻推空门",
        "后点包抄头球破门", "禁区内假摔骗过后卫后冷静施射"
    )

    private fun generateFootballMessages(): List<ServiceMessagePool> {
        val messages = mutableListOf<ServiceMessagePool>()
        repeat(MESSAGES_PER_SERVICE) {
            val match = matches.random()
            val scorer = scorers.random()
            val goalType = goalTypes.random()
            val minute = Random.nextInt(1, 91)
            val homeScore = Random.nextInt(0, 5)
            val awayScore = Random.nextInt(0, 5)
            val isHome = Random.nextBoolean()
            val scoringTeam = if (isHome) match.home else match.away

            messages.add(ServiceMessagePool().apply {
                serviceCode = "srv_football_goals"
                title = "⚽ [${match.league}] ${match.home} $homeScore-$awayScore ${match.away}"
                body = "${minute}' $scorer ($scoringTeam) $goalType"
            })
        }
        return messages
    }
}
