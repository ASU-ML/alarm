package com.example.alarm.gateway.web

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.Instant

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(private val jdbc: JdbcTemplate) {
	@GetMapping("/search")
	fun search(
		@RequestParam(required = false) actor: String?,
		@RequestParam(required = false) objectType: String?,
		@RequestParam(required = false) objectId: String?,
		@RequestParam(required = false) from: String?,
		@RequestParam(required = false) to: String?
	): List<Map<String, Any?>> {
		val sb = StringBuilder("SELECT ts_utc, actor_id::text, action, object_type, object_id, details, hash FROM audit_ledger WHERE 1=1")
		val args = mutableListOf<Any>()
		if (!actor.isNullOrBlank()) { sb.append(" AND actor_id::text=?"); args += actor }
		if (!objectType.isNullOrBlank()) { sb.append(" AND object_type=?"); args += objectType }
		if (!objectId.isNullOrBlank()) { sb.append(" AND object_id=?"); args += objectId }
		if (!from.isNullOrBlank()) { sb.append(" AND ts_utc>=?"); args += Timestamp.from(Instant.parse(from)) }
		if (!to.isNullOrBlank()) { sb.append(" AND ts_utc<=?"); args += Timestamp.from(Instant.parse(to)) }
		sb.append(" ORDER BY ts_utc DESC LIMIT 500")
		return jdbc.query(sb.toString(), args.toTypedArray()) { rs, _ ->
			mapOf(
				"ts_utc" to rs.getTimestamp(1).toInstant().toString(),
				"actor_id" to rs.getString(2),
				"action" to rs.getString(3),
				"object_type" to rs.getString(4),
				"object_id" to rs.getString(5),
				"details" to rs.getString(6),
				"hash" to rs.getString(7)
			)
		}
	}
}
