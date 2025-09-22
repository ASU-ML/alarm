package com.example.alarm.gateway.workflow

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class ApprovalsRepository(private val jdbc: JdbcTemplate) {
	fun startChangeThreshold(projectId: String, requestedBy: String, alarmId: String, newThreshold: Double, reason: String): String {
		val wfId = UUID.randomUUID()
		jdbc.update(
			"INSERT INTO approvals(workflow_id, state, requested_by, requested_at, reason, signature_hash, approver_id, decided_at) VALUES (?,?,?,?,?,NULL,NULL,NULL)",
			wfId, "pending", UUID.fromString(requestedBy), Timestamp.from(Instant.now()), reason
		)
		// Сохраним служебную запись (optionally в отдельной таблице). Для MVP оставим только approvals + audit/ops в других сервисах
		return wfId.toString()
	}

	fun approve(workflowId: String, approverId: String, signatureHash: String?): Boolean {
		val rows = jdbc.update(
			"UPDATE approvals SET state='approved', approver_id=?, decided_at=?, signature_hash=COALESCE(?, signature_hash) WHERE workflow_id=? AND state='pending'",
			UUID.fromString(approverId), Timestamp.from(Instant.now()), signatureHash, UUID.fromString(workflowId)
		)
		return rows > 0
	}
}
