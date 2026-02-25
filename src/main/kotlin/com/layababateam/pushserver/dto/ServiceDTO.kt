package com.layababateam.pushserver.dto

import jakarta.validation.constraints.NotNull

// 服务列表项响应
data class ServiceItemResponse(
    val id: Long,
    val serviceCode: String,
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val isSubscribed: Boolean
)

// 服务列表响应
data class ServiceListResponse(
    val items: List<ServiceItemResponse>
)

// 订阅/取消订阅请求
data class SubscribeRequest(
    @field:NotNull(message = "服务ID不能为空")
    val serviceId: Long
)
