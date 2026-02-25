package com.layababateam.pushserver.repository

import com.layababateam.pushserver.entity.PublicService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PublicServiceRepository : JpaRepository<PublicService, Long> {

    fun findByActiveTrue(): List<PublicService>

    fun findByServiceCode(serviceCode: String): PublicService?
}
