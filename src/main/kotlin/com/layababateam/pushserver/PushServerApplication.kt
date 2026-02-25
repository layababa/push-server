package com.layababateam.pushserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PushServerApplication

fun main(args: Array<String>) {
    runApplication<PushServerApplication>(*args)
}
