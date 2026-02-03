package com.layababateam.pushserver.service

import com.layababateam.pushserver.dto.LoginRequest
import com.layababateam.pushserver.dto.RegisterRequest
import com.layababateam.pushserver.dto.UserResponse
import com.layababateam.pushserver.entity.DeviceBinding
import com.layababateam.pushserver.entity.User
import com.layababateam.pushserver.repository.DeviceBindingRepository
import com.layababateam.pushserver.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserService(
    private val userRepo: UserRepository,
    private val deviceRepo: DeviceBindingRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    @Transactional
    fun register(req: RegisterRequest): UserResponse {
        // 检查用户名
        if (userRepo.existsByUsername(req.username)) {
            throw IllegalArgumentException("用户名已存在: ${req.username}")
        }
        
        // 手机号如果填了，检查是否重复
        req.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            if (userRepo.existsByPhone(phone)) {
                throw IllegalArgumentException("手机号已被使用")
            }
        }
        
        // 邮箱同理
        req.email?.takeIf { it.isNotBlank() }?.let { email ->
            if (userRepo.existsByEmail(email)) {
                throw IllegalArgumentException("邮箱已被使用")
            }
        }
        
        // 简单处理密码，实际项目要用BCrypt
        // TODO: 接入spring security后改成BCryptPasswordEncoder
        val user = User(
            username = req.username,
            password = req.password,  // 这里先明文存着，后面再加密
            phone = req.phone?.takeIf { it.isNotBlank() },
            email = req.email?.takeIf { it.isNotBlank() }
        )
        
        val saved = userRepo.save(user)
        log.info("new user registered: id={}, username={}", saved.id, saved.username)
        
        return toResponse(saved)
    }
    
    @Transactional
    fun login(req: LoginRequest, clientIp: String?): UserResponse {
        // 三合一查询
        val user = userRepo.findByAccount(req.account)
            .orElseThrow { IllegalArgumentException("账号不存在") }
        
        // 校验密码
        // TODO: 后面要改成BCrypt.matches
        if (user.password != req.password) {
            throw IllegalArgumentException("密码错误")
        }
        
        // 绑定设备
        bindDevice(user.id!!, req, clientIp)
        
        log.info("user login: id={}, device={}", user.id, req.deviceUuid)
        return toResponse(user)
    }
    
    // 绑定/更新设备信息
    private fun bindDevice(userId: Long, req: LoginRequest, clientIp: String?) {
        val existing = deviceRepo.findByDeviceUuid(req.deviceUuid)
        
        if (existing.isPresent) {
            // 设备已存在，更新绑定
            val device = existing.get()
            device.userId = userId
            device.lastLoginIp = clientIp
            device.lastActive = LocalDateTime.now()
            device.pushToken = req.pushToken ?: device.pushToken
            device.deviceType = req.deviceType ?: device.deviceType
            deviceRepo.save(device)
        } else {
            // 新设备
            val device = DeviceBinding(
                deviceUuid = req.deviceUuid,
                userId = userId,
                deviceType = req.deviceType,
                pushToken = req.pushToken,
                lastLoginIp = clientIp
            )
            deviceRepo.save(device)
        }
    }
    
    fun getUserById(id: Long): UserResponse? {
        return userRepo.findById(id)
            .map { toResponse(it) }
            .orElse(null)
    }
    
    private fun toResponse(user: User): UserResponse {
        return UserResponse(
            id = user.id!!,
            username = user.username,
            phone = user.phone,
            email = user.email
        )
    }
}
