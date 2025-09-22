package com.example.alarm.gateway.web

import com.example.alarm.gateway.events.EventService
import com.example.alarm.gateway.events.IngestEventDto
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class IngestController(private val service: EventService) {
	@PostMapping("/ingest/events")
	fun ingest(@Valid @RequestBody batch: List<IngestEventDto>): ResponseEntity<Void> {
		service.ingestBatch(batch)
		return ResponseEntity.accepted().build()
	}
}
