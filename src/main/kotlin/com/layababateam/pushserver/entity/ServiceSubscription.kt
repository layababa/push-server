package com.layababateam.pushserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "service_subscription",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_service", columnNames = ["user_id", "service_id"])
    ],
    indexes = [
        Index(name = "idx_sub_user_id", columnList = "user_id"),
        Index(name = "idx_sub_service_id", columnList = "service_id")
    ]
)
class ServiceSubscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "service_id", nullable = false)
    var serviceId: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
