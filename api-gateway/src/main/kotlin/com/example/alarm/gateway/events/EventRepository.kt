package com.example.alarm.gateway.events

import com.example.alarm.gateway.rls.ProjectContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.query
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class EventRepository(private val jdbc: JdbcTemplate) {
	private fun setProjectsGuc(projectsOverride: List<String>? = null) {
		val projects = projectsOverride ?: ProjectContext.get()
		val json = projects.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")
		jdbc.update("SET LOCAL alarm.subject_projects = ?::jsonb", json)
	}

	fun findAlarmIdByTag(projectId: String, tag: String): String? {
		setProjectsGuc()
		return jdbc.query("SELECT alarm_id::text FROM alarms WHERE project_id = ? AND tag = ? LIMIT 1", projectId, tag) { rs, _ ->
			rs.getString(1)
		}.firstOrNull()
	}

	fun findProjectIdByAlarmId(alarmId: String): String? {
		setProjectsGuc()
		return jdbc.query("SELECT project_id::text FROM alarms WHERE alarm_id = ? LIMIT 1", alarmId) { rs, _ -> rs.getString(1) }.firstOrNull()
	}

	fun getAlarmPriority(alarmId: String): Int? {
		setProjectsGuc()
		return jdbc.query("SELECT priority FROM alarms WHERE alarm_id = ?", alarmId) { rs, _ -> rs.getInt(1) }.firstOrNull()
	}

	fun insertEvent(e: NormalizedEvent) {
		setProjectsGuc()
		val exists = jdbc.query(
			"SELECT 1 FROM alarm_events WHERE project_id = ? AND source_id = ? AND source_seq = ? LIMIT 1",
			e.projectId, e.sourceId, e.sourceSeq
		) { _, _ -> 1 }.isNotEmpty()
		if (exists) return
		jdbc.update(
			"INSERT INTO alarm_events(ts_utc, event_type, alarm_id, value, operator_id, note, source_id, source_seq, project_id) VALUES (?,?,?,?,?,?,?,?,?)",
			Timestamp.from(e.tsUtc), e.eventType, e.alarmId, e.value, e.operatorId, e.note, e.sourceId, e.sourceSeq, e.projectId
		)
	}

	fun recordAction(eventType: String, alarmId: String, operatorId: String?, note: String?, projectId: String, at: Instant = Instant.now()) {
		setProjectsGuc()
		jdbc.update(
			"INSERT INTO alarm_events(ts_utc, event_type, alarm_id, value, operator_id, note, source_id, source_seq, project_id) VALUES (?,?,?,?,?,?,?,?,?)",
			Timestamp.from(at), eventType, alarmId, null, operatorId, note, "api", at.toEpochMilli(), projectId
		)
	}

	fun fetchSince(projects: List<String>, since: Instant, limit: Int = 500): List<NormalizedEvent> {
		setProjectsGuc(projects)
		return jdbc.query(
			"""
			SELECT ts_utc, event_type, alarm_id::text, value, operator_id::text, note, source_id, source_seq, project_id::text
			FROM alarm_events
			WHERE ts_utc > ? AND project_id::text = ANY (?::text[])
			ORDER BY ts_utc ASC
			LIMIT ?
			""".trimIndent(),
			Timestamp.from(since), projects.toTypedArray(), limit
		) { rs, _ ->
			NormalizedEvent(
				tsUtc = rs.getTimestamp(1).toInstant(),
				eventType = rs.getString(2),
				alarmId = rs.getString(3),
				value = rs.getDouble(4).let { if (rs.wasNull()) null else it },
				operatorId = rs.getString(5),
				note = rs.getString(6),
				sourceId = rs.getString(7),
				sourceSeq = rs.getLong(8),
				projectId = rs.getString(9)
			)
		}
	}
}
