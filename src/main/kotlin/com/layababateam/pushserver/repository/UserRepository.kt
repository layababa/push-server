package com.layababateam.pushserver.repository

import com.layababateam.pushserver.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {
    
    fun findByUsername(username: String): Optional<User>
    
    fun findByPhone(phone: String): Optional<User>
    
    fun findByEmail(email: String): Optional<User>
    
    // 三合一登录查询，username/phone/email都能登
    @Query("""
        SELECT u FROM User u 
        WHERE u.username = :account 
           OR u.phone = :account 
           OR u.email = :account
    """)
    fun findByAccount(@Param("account") account: String): Optional<User>
    
    // 检查是否已存在
    fun existsByUsername(username: String): Boolean
    
    fun existsByPhone(phone: String): Boolean
    
    fun existsByEmail(email: String): Boolean
}
