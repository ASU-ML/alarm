package com.example.alarm.gateway.web

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.Instant

@RestController
@RequestMapping("/api/v1/trends")
class TrendsController(private val jdbc: JdbcTemplate) {
	@GetMapping("/series")
	fun series(
		@RequestParam tags: String,
		@RequestParam from: String,
		@RequestParam to: String,
		@RequestParam(required=false, defaultValue = "60") stepSec: Long
	): Map<String, Any> {
		val fromTs = Timestamp.from(Instant.parse(from))
		val toTs = Timestamp.from(Instant.parse(to))
		val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
		val data = mutableMapOf<String, List<List<Any>>>()
		tagList.forEach { tag ->
			val rows: List<List<Any>> = jdbc.query(
				"""
				SELECT date_trunc('second', ts_utc) AS ts, avg(value) AS v
				FROM alarm_events e JOIN alarms a ON e.alarm_id=a.alarm_id
				WHERE a.tag=? AND e.ts_utc BETWEEN ? AND ? AND e.value IS NOT NULL
				GROUP BY 1 ORDER BY 1
				""".trimIndent(),
				arrayOf(tag, fromTs, toTs)
			) { rs, _ -> listOf(rs.getTimestamp(1).toInstant().toString(), rs.getDouble(2)) }
			// простое даунсэмплирование по шагу: берём каждый k-й элемент, где k ~ stepSec (мин 1)
			val k = if (stepSec < 1) 1 else stepSec.toInt()
			val sampled = rows.withIndex().filter { it.index % k == 0 }.map { it.value }
			data[tag] = sampled
		}
		return mapOf("series" to data)
	}
}
