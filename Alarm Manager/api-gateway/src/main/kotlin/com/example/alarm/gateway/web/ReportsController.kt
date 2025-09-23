package com.example.alarm.gateway.web

import com.example.alarm.gateway.reports.ReportService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/v1/reports")
class ReportsController(private val service: ReportService) {
	@PostMapping("/run")
	fun run(@RequestParam from: String, @RequestParam to: String, @RequestParam format: String): ResponseEntity<Map<String, String>> {
		val path = service.generateStandard(Instant.parse(from), Instant.parse(to), format)
		return ResponseEntity.accepted().body(mapOf("file" to path.toString()))
	}

	data class TemplateSection(val title: String, val metrics: List<String>)
	data class TemplateReq(val title: String, val sections: List<TemplateSection>, val format: String, val from: String, val to: String)

	@PostMapping("/run-template")
	fun runTemplate(@RequestBody req: TemplateReq): ResponseEntity<Map<String, String>> {
		val path = service.generateTemplate(req.title, req.sections.map { it.title to it.metrics }, Instant.parse(req.from), Instant.parse(req.to), req.format)
		return ResponseEntity.accepted().body(mapOf("file" to path.toString()))
	}
}
