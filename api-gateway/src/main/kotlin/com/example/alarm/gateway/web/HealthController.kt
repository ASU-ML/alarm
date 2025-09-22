package com.example.alarm.gateway.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
	@GetMapping("/api/health")
	fun health() = mapOf("status" to "OK")
}
