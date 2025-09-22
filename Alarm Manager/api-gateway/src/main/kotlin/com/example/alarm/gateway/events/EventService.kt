package com.example.alarm.gateway.events

import org.springframework.stereotype.Service

@Service
class EventService(private val repo: EventRepository) {
	fun ingestBatch(events: List<IngestEventDto>) {
		for (e in events) {
			val alarmId = repo.findAlarmIdByTag(e.projectId, e.alarmTag)
			val normalized = NormalizedEvent(
				tsUtc = e.tsUtc,
				eventType = e.eventType,
				alarmId = alarmId,
				value = e.value,
				operatorId = null,
				note = e.note,
				sourceId = e.sourceId,
				sourceSeq = e.sourceSeq,
				projectId = e.projectId,
			)
			repo.insertEvent(normalized)
		}
	}
}
