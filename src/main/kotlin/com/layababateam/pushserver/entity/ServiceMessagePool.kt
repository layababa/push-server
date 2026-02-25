package com.layababateam.pushserver.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "service_message_pool",
    indexes = [
        Index(name = "idx_pool_service_code", columnList = "service_code"),
        Index(name = "idx_pool_service_code_id", columnList = "service_code, id")
    ]
)
class ServiceMessagePool(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "service_code", nullable = false, length = 50)
    var serviceCode: String = "",

    @Column(nullable = false, length = 200)
    var title: String = "",

    @Column(nullable = false, length = 1000)
    var body: String = ""
)
