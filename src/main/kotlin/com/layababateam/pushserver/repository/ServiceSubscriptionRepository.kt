package com.layababateam.pushserver.repository

import com.layababateam.pushserver.entity.ServiceSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ServiceSubscriptionRepository : JpaRepository<ServiceSubscription, Long> {

    fun findByUserId(userId: Long): List<ServiceSubscription>

    fun findByUserIdAndServiceId(userId: Long, serviceId: Long): Optional<ServiceSubscription>

    fun existsByUserIdAndServiceId(userId: Long, serviceId: Long): Boolean

    fun deleteByUserIdAndServiceId(userId: Long, serviceId: Long)

    // 查询某个服务的所有订阅者 userId
    @Query("SELECT s.userId FROM ServiceSubscription s WHERE s.serviceId = :serviceId")
    fun findUserIdsByServiceId(@Param("serviceId") serviceId: Long): List<Long>
}
