package com.example.alarm.gateway.events

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class IngestEventDto(
	@NotBlank val eventType: String,
	@NotBlank val alarmTag: String,
	val value: Double?,
	val note: String?,
	@NotBlank val sourceId: String,
	@NotNull val sourceSeq: Long,
	@NotNull val tsUtc: Instant,
	@NotBlank val projectId: String,
)

data class NormalizedEvent(
	val tsUtc: Instant,
	val eventType: String,
	val alarmId: String?,
	val value: Double?,
	val operatorId: String?,
	val note: String?,
	val sourceId: String,
	val sourceSeq: Long,
	val projectId: String,
)
