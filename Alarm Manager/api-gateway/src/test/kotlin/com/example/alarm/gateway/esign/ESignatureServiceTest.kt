package com.example.alarm.gateway.esign

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ESignatureServiceTest {
	@Test
	fun hashAndVerify() {
		val svc = ESignatureService()
		val h = svc.hashPayload("test")
		assertEquals(64, h.length)
		assertTrue(svc.verify("123"))
	}
}
