package com.example.alarm.gateway.web

import com.example.alarm.gateway.events.EventRepository
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api/v1")
class EventStreamController(private val repo: EventRepository) {
	@GetMapping("/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
	fun stream(@AuthenticationPrincipal jwt: Jwt): Flux<ServerSentEvent<Map<String, Any?>>> {
		@Suppress("UNCHECKED_CAST")
		val projects = ((jwt.claims["resource_access"] as? Map<*, *>)?.get("alarm") as? Map<*, *>)
			?.get("projects") as? List<String> ?: emptyList()
		var last = Instant.now().minusSeconds(5)
		return Flux.interval(Duration.ofSeconds(1)).flatMap {
			val events = repo.fetchSince(projects, last)
			if (events.isNotEmpty()) last = events.last().tsUtc
			Flux.fromIterable(events.map { e ->
				ServerSentEvent.builder<Map<String, Any?>>()
					.event("alarm_event")
					.data(
						mapOf(
							"ts_utc" to e.tsUtc.toString(),
							"event_type" to e.eventType,
							"alarm_id" to e.alarmId,
							"value" to e.value,
							"note" to e.note,
							"project_id" to e.projectId
						)
					)
					.build()
			})
		}
	}
}
