package com.layababateam.pushserver.controller

import com.layababateam.pushserver.dto.ApiResult
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    // validation失败
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(e: MethodArgumentNotValidException): ApiResult<Nothing> {
        val msg = e.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ApiResult.fail(msg, ApiResult.CODE_BAD_REQUEST)
    }
    
    // 缺少必要的header
    @ExceptionHandler(MissingRequestHeaderException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingHeader(e: MissingRequestHeaderException): ApiResult<Nothing> {
        return ApiResult.fail("缺少header: ${e.headerName}", ApiResult.CODE_BAD_REQUEST)
    }
    
    // 业务异常
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(e: IllegalArgumentException): ApiResult<Nothing> {
        return ApiResult.fail(e.message ?: "参数错误", ApiResult.CODE_BAD_REQUEST)
    }
    
    // 兜底
    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception): ApiResult<Nothing> {
        log.error("unexpected error", e)
        return ApiResult.fail("服务器内部错误", ApiResult.CODE_INTERNAL_ERROR)
    }
}
