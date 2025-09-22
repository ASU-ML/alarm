package com.example.alarm.gateway.web

import com.example.alarm.gateway.actions.OperatorActionsRepository
import com.example.alarm.gateway.audit.AuditLedgerService
import com.example.alarm.gateway.esign.ESignatureService
import com.example.alarm.gateway.workflow.ApprovalsRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/workflow")
class WorkflowController(
	private val approvals: ApprovalsRepository,
	private val esign: ESignatureService,
	private val audit: AuditLedgerService,
	private val ops: OperatorActionsRepository
) {
	data class ChangeThresholdReq(val projectId: String, val alarmId: String, val newThreshold: Double, val reason: String, val passcode: String?)

	@PostMapping("/change-threshold")
	fun start(@RequestBody req: ChangeThresholdReq, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
		val signed = !req.passcode.isNullOrBlank() && esign.verify(req.passcode!!)
		val wfId = approvals.startChangeThreshold(req.projectId, jwt.subject, req.alarmId, req.newThreshold, req.reason)
		val signatureHash = if (signed) esign.hashPayload("WF:$wfId:${jwt.subject}") else null
		ops.record(jwt.subject, "WF_START_CHANGE_THRESHOLD", "{\"workflow_id\":\"$wfId\",\"alarm_id\":\"${req.alarmId}\",\"new_threshold\":${req.newThreshold}}", req.projectId, wfId, signatureHash)
		audit.append(jwt.subject, "WF_START", "workflow", wfId, "{\"project_id\":\"${req.projectId}\"}")
		return ResponseEntity.accepted().body(mapOf("workflowId" to wfId, "signed" to signed))
	}

	@PostMapping("/approve/{workflowId}")
	fun approve(@PathVariable workflowId: String, @RequestBody body: Map<String, String?>, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
		val passcode = body["passcode"]
		val signed = !passcode.isNullOrBlank() && esign.verify(passcode!!)
		val signatureHash = if (signed) esign.hashPayload("WF_APPROVE:$workflowId:${jwt.subject}") else null
		val ok = approvals.approve(workflowId, jwt.subject, signatureHash)
		return if (ok) {
			ops.record(jwt.subject, "WF_APPROVE", "{\"workflow_id\":\"$workflowId\"}", jwt.claims["project"]?.toString() ?: "", workflowId, signatureHash)
			audit.append(jwt.subject, "WF_APPROVE", "workflow", workflowId, "{}")
			ResponseEntity.ok(mapOf("status" to "approved", "workflowId" to workflowId, "signed" to signed))
		} else {
			ResponseEntity.status(409).body(mapOf("error" to "not pending"))
		}
	}
}
