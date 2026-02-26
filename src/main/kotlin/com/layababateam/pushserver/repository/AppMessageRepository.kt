package com.layababateam.pushserver.repository

import com.layababateam.pushserver.entity.AppMessage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AppMessageRepository : JpaRepository<AppMessage, Long> {
    
    // 分页查用户消息，按时间倒序
    fun findByReceiverIdOrderByCreatedAtDesc(receiverId: Long, pageable: Pageable): Page<AppMessage>
    
    // 分页查用户消息，带时间过滤（hours 参数）
    @Query("""
        SELECT m FROM AppMessage m 
        WHERE m.receiverId = :receiverId 
          AND m.createdAt >= :since 
        ORDER BY m.createdAt DESC
    """)
    fun findByReceiverIdAndCreatedAtAfter(
        @Param("receiverId") receiverId: Long,
        @Param("since") since: LocalDateTime,
        pageable: Pageable
    ): Page<AppMessage>
    
    // 查未读消息数
    fun countByReceiverIdAndReadStatus(receiverId: Long, readStatus: Int): Long
    
    // 标记已读
    @Modifying
    @Query("UPDATE AppMessage m SET m.readStatus = 1, m.readAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    fun markAsRead(@Param("id") id: Long): Int
    
    // 批量标记已读
    @Modifying
    @Query("UPDATE AppMessage m SET m.readStatus = 1, m.readAt = CURRENT_TIMESTAMP WHERE m.receiverId = :userId AND m.readStatus = 0")
    fun markAllAsRead(@Param("userId") userId: Long): Int
    
    // 更新推送状态
    @Modifying
    @Query("UPDATE AppMessage m SET m.pushStatus = :status WHERE m.id = :id")
    fun updatePushStatus(@Param("id") id: Long, @Param("status") status: Int): Int

    // 删除用户所有消息（清空消息 / 账号注销用）
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AppMessage m WHERE m.receiverId = :userId")
    fun deleteByReceiverId(@Param("userId") userId: Long): Int
}
