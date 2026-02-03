package com.layababateam.pushserver.dto

// 统一响应格式
data class ApiResult<T>(
    val code: Int,
    val msg: String,
    val data: T? = null
) {
    companion object {
        fun <T> ok(data: T? = null, msg: String = "success"): ApiResult<T> {
            return ApiResult(0, msg, data)
        }
        
        fun <T> fail(msg: String, code: Int = -1): ApiResult<T> {
            return ApiResult(code, msg, null)
        }
        
        // 常用错误码
        const val CODE_NOT_FOUND = 404
        const val CODE_BAD_REQUEST = 400
        const val CODE_UNAUTHORIZED = 401
        const val CODE_INTERNAL_ERROR = 500
    }
}
