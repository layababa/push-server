package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.ApiResult
import com.layababateam.pushserver.dto.ServiceListResponse
import com.layababateam.pushserver.dto.SubscribeRequest
import com.layababateam.pushserver.service.SubscriptionService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/**
 * 公共服务订阅 Controller
 * 提供服务列表查询、订阅、取消订阅接口，均需 JWT 认证
 */
@RestController
@RequestMapping("/api/v1/services")
class ServiceController(
    private val subscriptionService: SubscriptionService
) {

    /**
     * 获取可用公共服务列表（含订阅状态）
     * GET /api/v1/services/list
     */
    @GetMapping("/list")
    fun listServices(
        @RequestAttribute("userId") userId: Long
    ): ApiResult<ServiceListResponse> {
        val result = subscriptionService.listServices(userId)
        return ApiResult.ok(result)
    }

    /**
     * 订阅服务
     * POST /api/v1/services/subscribe
     */
    @PostMapping("/subscribe")
    fun subscribe(
        @RequestAttribute("userId") userId: Long,
        @Valid @RequestBody req: SubscribeRequest
    ): ApiResult<Nothing> {
        val success = subscriptionService.subscribe(userId, req.serviceId)
        return if (success) {
            ApiResult.ok(msg = "订阅成功")
        } else {
            ApiResult.fail("服务不存在", ApiResult.CODE_NOT_FOUND)
        }
    }

    /**
     * 取消订阅服务
     * POST /api/v1/services/unsubscribe
     */
    @PostMapping("/unsubscribe")
    fun unsubscribe(
        @RequestAttribute("userId") userId: Long,
        @Valid @RequestBody req: SubscribeRequest
    ): ApiResult<Nothing> {
        val success = subscriptionService.unsubscribe(userId, req.serviceId)
        return if (success) {
            ApiResult.ok(msg = "取消订阅成功")
        } else {
            ApiResult.fail("服务不存在", ApiResult.CODE_NOT_FOUND)
        }
    }
}
