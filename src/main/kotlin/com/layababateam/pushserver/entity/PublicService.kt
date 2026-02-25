package com.layababateam.pushserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "public_service",
    indexes = [
        Index(name = "idx_service_code", columnList = "service_code", unique = true)
    ]
)
class PublicService(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "service_code", nullable = false, length = 50, unique = true)
    var serviceCode: String = "",

    @Column(nullable = false, length = 100)
    var name: String = "",

    @Column(length = 500)
    var description: String? = null,

    @Column(name = "icon_url", length = 500)
    var iconUrl: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
