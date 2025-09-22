package com.example.alarm.gateway.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class ApiControllers {
	@PostMapping("/ingest/events")
	fun ingestEvents(@RequestBody body: Any): ResponseEntity<Void> = ResponseEntity.accepted().build()

	@GetMapping("/alarms")
	fun searchAlarms(@RequestParam project: String?, @RequestParam q: String?): List<Map<String, Any?>> = emptyList()

	@PostMapping("/alarms/{id}/ack")
	fun ack(@PathVariable id: String): Map<String, String> = mapOf("status" to "ACKED", "id" to id)

	@PostMapping("/alarms/{id}/shelve")
	fun shelve(@PathVariable id: String, @RequestParam reason: String, @RequestParam ttlMinutes: Int): Map<String, Any> =
		mapOf("status" to "SHELVED", "id" to id, "ttl" to ttlMinutes)

	@PostMapping("/alarms/{id}/unshelve")
	fun unshelve(@PathVariable id: String): Map<String, String> = mapOf("status" to "UNSHELVED", "id" to id)

	@GetMapping("/events/stream")
	fun stream(): ResponseEntity<Void> = ResponseEntity.ok().build()

	@GetMapping("/kpi/standard")
	fun kpiStandard(@RequestParam from: String?, @RequestParam to: String?): Map<String, Any> = mapOf("alm_rate" to 0)

	@PostMapping("/reports/run")
	fun runReport(@RequestBody body: Any): ResponseEntity<Void> = ResponseEntity.accepted().build()

	@GetMapping("/trends/series")
	fun trends(@RequestParam tags: String, @RequestParam from: String, @RequestParam to: String, @RequestParam step: String): Any = emptyList<Any>()

	@PostMapping("/workflow/change-threshold")
	fun startWorkflow(@RequestBody body: Any): ResponseEntity<Void> = ResponseEntity.accepted().build()

	@PostMapping("/workflow/approve/{workflowId}")
	fun approve(@PathVariable workflowId: String, @RequestBody body: Any?): Map<String, String> = mapOf("workflowId" to workflowId, "status" to "approved")

	@GetMapping("/audit/search")
	fun auditSearch(): List<Any> = emptyList()
}
