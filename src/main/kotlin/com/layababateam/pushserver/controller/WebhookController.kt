package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.ApiResult
import com.layababateam.pushserver.dto.WebhookPushRequest
import com.layababateam.pushserver.dto.WebhookPushResponse
import com.layababateam.pushserver.service.MessageService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

/**
 * Webhook 推送 Controller — pushKey 认证，无需 JWT
 * 
 * GET  /push/{pushKey}/{title}/{body}  — 极简模式
 * POST /push/{pushKey}                 — 完整字段模式
 */
@RestController
@RequestMapping("/push")
class WebhookController(
    private val msgService: MessageService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    /**
     * GET 极简推送: /push/{pushKey}/{title}/{body}
     * 可选 query params: url, group, icon
     */
    @GetMapping("/{pushKey}/{title}/{body}")
    fun pushViaGet(
        @PathVariable pushKey: String,
        @PathVariable title: String,
        @PathVariable body: String,
        @RequestParam(required = false) url: String?,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) icon: String?
    ): ApiResult<WebhookPushResponse> {
        return try {
            val result = msgService.pushViaWebhook(pushKey, title, body, url, group)
            ApiResult.ok(result)
        } catch (e: IllegalArgumentException) {
            log.warn("webhook push failed: pushKey={}, err={}", pushKey.take(12) + "***", e.message)
            ApiResult.fail(e.message ?: "推送失败", ApiResult.CODE_NOT_FOUND)
        } catch (e: Exception) {
            log.error("webhook push error: pushKey={}", pushKey.take(12) + "***", e)
            ApiResult.fail("服务器内部错误", ApiResult.CODE_INTERNAL_ERROR)
        }
    }
    
    /**
     * GET 仅标题推送: /push/{pushKey}/{title}
     * body 为空
     */
    @GetMapping("/{pushKey}/{title}")
    fun pushViaGetTitleOnly(
        @PathVariable pushKey: String,
        @PathVariable title: String,
        @RequestParam(required = false) url: String?,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) icon: String?
    ): ApiResult<WebhookPushResponse> {
        return try {
            val result = msgService.pushViaWebhook(pushKey, title, null, url, group)
            ApiResult.ok(result)
        } catch (e: IllegalArgumentException) {
            log.warn("webhook push failed: pushKey={}, err={}", pushKey.take(12) + "***", e.message)
            ApiResult.fail(e.message ?: "推送失败", ApiResult.CODE_NOT_FOUND)
        } catch (e: Exception) {
            log.error("webhook push error: pushKey={}", pushKey.take(12) + "***", e)
            ApiResult.fail("服务器内部错误", ApiResult.CODE_INTERNAL_ERROR)
        }
    }
    
    /**
     * POST 完整推送: /push/{pushKey}
     * JSON body 支持 title, body, url, group, icon
     */
    @PostMapping("/{pushKey}")
    fun pushViaPost(
        @PathVariable pushKey: String,
        @RequestBody req: WebhookPushRequest
    ): ApiResult<WebhookPushResponse> {
        return try {
            val result = msgService.pushViaWebhook(pushKey, req.title, req.body, req.url, req.group)
            ApiResult.ok(result)
        } catch (e: IllegalArgumentException) {
            log.warn("webhook push failed: pushKey={}, err={}", pushKey.take(12) + "***", e.message)
            ApiResult.fail(e.message ?: "推送失败", ApiResult.CODE_NOT_FOUND)
        } catch (e: Exception) {
            log.error("webhook push error: pushKey={}", pushKey.take(12) + "***", e)
            ApiResult.fail("服务器内部错误", ApiResult.CODE_INTERNAL_ERROR)
        }
    }
}
