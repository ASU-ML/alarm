package com.example.alarm.gateway.audit

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class AuditLedgerService(private val jdbc: JdbcTemplate) {
	private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

	fun append(actorId: String?, action: String, objectType: String, objectId: String, detailsJson: String) {
		val prevHash = jdbc.query("SELECT hash FROM audit_ledger ORDER BY seq DESC LIMIT 1") { rs, _ -> rs.getString(1) }.firstOrNull()
		val payload = detailsJson
		val base = (prevHash ?: "") + payload
		val hash = sha256(base)
		jdbc.update(
			"INSERT INTO audit_ledger(ts_utc, actor_id, action, object_type, object_id, details, prev_hash, hash) VALUES (?,?,?,?,?,?::jsonb,?,?)",
			Timestamp.from(Instant.now()), actorId?.let(UUID::fromString), action, objectType, objectId, payload, prevHash, hash
		)
	}
}
