package com.example.alarm.gateway.web

import com.example.alarm.gateway.actions.OperatorActionsRepository
import com.example.alarm.gateway.audit.AuditLedgerService
import com.example.alarm.gateway.esign.ESignatureService
import com.example.alarm.gateway.events.EventRepository
import com.example.alarm.gateway.opa.OpaClient
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AlarmActionsController(
	private val opa: OpaClient,
	private val repo: EventRepository,
	private val esign: ESignatureService,
	private val actions: OperatorActionsRepository,
	private val audit: AuditLedgerService
) {
	@PostMapping("/alarms/{id}/ack")
	fun ack(@PathVariable id: String, @AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) passcode: String?): ResponseEntity<Any> {
		val allowed = opa.allow(
			action = "ACK",
			jwt = jwt.claims,
			resource = mapOf("project_id" to (jwt.claims["project"] ?: repo.findProjectIdByAlarmId(id) ?: ""), "alarm_id" to id)
		)
		if (!allowed) return ResponseEntity.status(403).body(mapOf("error" to "OPA deny"))
		val projectId = repo.findProjectIdByAlarmId(id) ?: return ResponseEntity.status(404).body(mapOf("error" to "not found"))
		val priority = repo.getAlarmPriority(id) ?: 4
		var sigOk = false
		var signatureHash: String? = null
		if (priority <= 2) {
			if (passcode.isNullOrBlank() || !esign.verify(passcode)) {
				return ResponseEntity.status(412).body(mapOf("error" to "e-signature required for P1/P2"))
			}
			signatureHash = esign.hashPayload("ACK:$id:${jwt.subject}")
			sigOk = true
		}
		repo.recordAction("ACK", id, jwt.subject, null, projectId)
		actions.record(jwt.subject, "ACK", "{\"alarm_id\":\"$id\"}", projectId, null, signatureHash)
		audit.append(jwt.subject, "ACK", "alarm", id, "{\"project_id\":\"$projectId\",\"signed\":$sigOk}")
		return ResponseEntity.ok(mapOf("status" to "ACKED", "id" to id, "signed" to sigOk))
	}

	@PostMapping("/alarms/{id}/rtn")
	fun rtn(@PathVariable id: String, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
		val projectId = repo.findProjectIdByAlarmId(id) ?: return ResponseEntity.status(404).body(mapOf("error" to "not found"))
		repo.recordAction("RTN", id, jwt.subject, null, projectId)
		actions.record(jwt.subject, "RTN", "{\"alarm_id\":\"$id\"}", projectId, null, null)
		audit.append(jwt.subject, "RTN", "alarm", id, "{\"project_id\":\"$projectId\"}")
		return ResponseEntity.ok(mapOf("status" to "RTN", "id" to id))
	}

	@PostMapping("/alarms/{id}/shelve")
	fun shelve(@PathVariable id: String, @RequestParam reason: String, @RequestParam ttlMinutes: Int, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
		val projectId = repo.findProjectIdByAlarmId(id) ?: return ResponseEntity.status(404).body(mapOf("error" to "not found"))
		repo.recordAction("SHELVE", id, jwt.subject, "$reason; ttl=${ttlMinutes}m", projectId)
		actions.record(jwt.subject, "SHELVE", "{\"alarm_id\":\"$id\",\"reason\":\"$reason\",\"ttl\":$ttlMinutes}", projectId, null, null)
		audit.append(jwt.subject, "SHELVE", "alarm", id, "{\"project_id\":\"$projectId\",\"reason\":\"$reason\",\"ttl\":$ttlMinutes}")
		return ResponseEntity.ok(mapOf("status" to "SHELVED", "id" to id, "ttl" to ttlMinutes))
	}

	@PostMapping("/alarms/{id}/unshelve")
	fun unshelve(@PathVariable id: String, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
		val projectId = repo.findProjectIdByAlarmId(id) ?: return ResponseEntity.status(404).body(mapOf("error" to "not found"))
		repo.recordAction("UNSHELVE", id, jwt.subject, null, projectId)
		actions.record(jwt.subject, "UNSHELVE", "{\"alarm_id\":\"$id\"}", projectId, null, null)
		audit.append(jwt.subject, "UNSHELVE", "alarm", id, "{\"project_id\":\"$projectId\"}")
		return ResponseEntity.ok(mapOf("status" to "UNSHELVED", "id" to id))
	}
}
