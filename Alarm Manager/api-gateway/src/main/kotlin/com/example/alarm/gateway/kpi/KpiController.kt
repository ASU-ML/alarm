package com.example.alarm.gateway.kpi

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.Instant

@RestController
@RequestMapping("/api/v1/kpi")
class KpiController(private val jdbc: JdbcTemplate) {
	@GetMapping("/standard")
	fun standard(@RequestParam from: String, @RequestParam to: String): Map<String, Any?> {
		val fromTs = Timestamp.from(Instant.parse(from))
		val toTs = Timestamp.from(Instant.parse(to))
		val almRate = jdbc.queryForObject(
			"SELECT count(*)::double precision / GREATEST(EXTRACT(EPOCH FROM (?::timestamptz - ?::timestamptz))/60,1) FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc BETWEEN ? AND ?",
			Double::class.java,
			toTs, fromTs, fromTs, toTs
		) ?: 0.0
		val standing = jdbc.queryForObject(
			"SELECT count(DISTINCT alarm_id) FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc<=? AND alarm_id NOT IN (SELECT alarm_id FROM alarm_events WHERE event_type='RTN' AND ts_utc BETWEEN ? AND ?)",
			Int::class.java,
			toTs, fromTs, toTs
		) ?: 0
		val p95Ack = jdbc.queryForObject(
			"""
			WITH pairs AS (
				SELECT i.alarm_id, i.ts_utc AS in_ts, (SELECT a.ts_utc FROM alarm_events a WHERE a.alarm_id=i.alarm_id AND a.event_type='ACK' AND a.ts_utc>=i.ts_utc ORDER BY a.ts_utc ASC LIMIT 1) AS ack_ts
				FROM alarm_events i WHERE i.event_type='ALM_IN' AND i.ts_utc BETWEEN ? AND ?
			)
			SELECT percentile_disc(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (ack_ts - in_ts))) FROM pairs WHERE ack_ts IS NOT NULL
			""".trimIndent(),
			Double::class.java,
			fromTs, toTs
		) ?: 0.0
		val chattering = jdbc.queryForObject(
			"""
			SELECT count(*) FROM (
				SELECT alarm_id FROM alarm_events WHERE ts_utc BETWEEN ? AND ? GROUP BY alarm_id HAVING COUNT(*)>50
			) t
			""".trimIndent(),
			Int::class.java,
			fromTs, toTs
		) ?: 0
		val topBad = jdbc.query(
			"""
			SELECT alarm_id::text, COUNT(*) AS cnt FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc BETWEEN ? AND ? GROUP BY alarm_id ORDER BY cnt DESC LIMIT 10
			""".trimIndent(),
			arrayOf(fromTs, toTs)
		) { rs, _ -> mapOf("alarm_id" to rs.getString(1), "count" to rs.getInt(2)) }
		return mapOf(
			"alm_rate_per_min" to almRate,
			"standing_alarms" to standing,
			"p95_ack_seconds" to p95Ack,
			"chattering_count" to chattering,
			"top_bad_actors" to topBad
		)
	}
}
