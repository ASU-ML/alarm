package com.example.alarm.gateway.web

import com.example.alarm.gateway.reports.ReportService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/reports")
class ReportsController(private val service: ReportService) {
	@PostMapping("/run")
	fun run(@RequestParam from: String, @RequestParam to: String, @RequestParam format: String): ResponseEntity<Map<String, String>> {
		val path = service.generateStandard(Instant.parse(from), Instant.parse(to), format)
		return ResponseEntity.accepted().body(mapOf("file" to path.toString()))
	}
}
