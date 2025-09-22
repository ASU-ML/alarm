package com.example.alarm.gateway.esign

import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class ESignatureService {
	fun hashPayload(payload: String): String {
		val md = MessageDigest.getInstance("SHA-256")
		return md.digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
	}
	fun verify(passcode: String): Boolean {
		// Заглушка: в реале сверка с вторым фактором/секретом пользователя
		return passcode.isNotBlank()
	}
}
