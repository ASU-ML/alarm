package com.example.alarm.gateway.opa

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class OpaClientTest {
	lateinit var server: WireMockServer

	@BeforeAll
	fun start() {
		server = WireMockServer(WireMockConfiguration.options().dynamicPort())
		server.start()
		server.stubFor(post(urlEqualTo("/v1/data/alarm/authz")).willReturn(aResponse().withHeader("Content-Type","application/json").withBody("{\"result\":true}")))
	}

	@AfterAll
	fun stop() { server.stop() }

	@Test
	fun allowTrue() {
		val client = OpaClient("http://localhost:${server.port()}/v1/data/alarm/authz")
		val ok = client.allow("ACK", mapOf("sub" to "u"), mapOf("project_id" to "p"))
		assertTrue(ok)
	}
}
