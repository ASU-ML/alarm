package com.example.alarm.gateway.actions

import com.example.alarm.gateway.rls.ProjectContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class OperatorActionsRepository(private val jdbc: JdbcTemplate) {
	private fun setProjectsGuc() {
		val projects = ProjectContext.get()
		val json = projects.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")
		jdbc.update("SET LOCAL alarm.subject_projects = ?::jsonb", json)
	}
	fun record(operatorId: String, actionType: String, payloadJson: String, projectId: String, workflowId: String?, signatureHash: String?) {
		setProjectsGuc()
		jdbc.update(
			"INSERT INTO operator_actions(action_id, ts_utc, operator_id, action_type, payload, project_id, workflow_id) VALUES (?,?,?,?,?::jsonb,?,?)",
			UUID.randomUUID(), Timestamp.from(Instant.now()), UUID.fromString(operatorId), actionType, payloadJson, UUID.fromString(projectId), workflowId?.let(UUID::fromString)
		)
		if (signatureHash != null) {
			jdbc.update(
				"UPDATE approvals SET signature_hash=? WHERE workflow_id=?",
				signatureHash, workflowId?.let(UUID::fromString)
			)
		}
	}
}
