package com.example.alarm.gateway.opa

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class OpaClient(@Value("\${opa.url}") private val opaUrl: String) {
	private val rest = RestTemplate()
	fun allow(action: String, jwt: Map<String, Any?>, resource: Map<String, Any?>): Boolean {
		val payload = mapOf("input" to mapOf("action" to action, "jwt" to jwt, "resource" to resource))
		val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
		val resp = rest.postForEntity(opaUrl, HttpEntity(payload, headers), Map::class.java)
		return (resp.body as? Map<*, *>)?.get("result") as? Boolean ?: false
	}
}
