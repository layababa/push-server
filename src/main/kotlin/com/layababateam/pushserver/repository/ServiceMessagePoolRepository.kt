package com.layababateam.pushserver.repository

import com.layababateam.pushserver.entity.ServiceMessagePool
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ServiceMessagePoolRepository : JpaRepository<ServiceMessagePool, Long> {

    fun countByServiceCode(serviceCode: String): Long

    // 随机取一条
    @Query(
        value = "SELECT * FROM service_message_pool WHERE service_code = :code ORDER BY RANDOM() LIMIT 1",
        nativeQuery = true
    )
    fun findRandomByServiceCode(@Param("code") serviceCode: String): ServiceMessagePool?
}
