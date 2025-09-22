package com.example.alarm.gateway.config

import com.example.alarm.gateway.rls.ProjectContext
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class RlsFilter : Filter {
	override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
		try {
			val auth = SecurityContextHolder.getContext().authentication
			val projects = (auth?.principal as? Jwt)
				?.claims
				?.get("resource_access")
				?.let { it as? Map<*, *> }
				?.get("alarm")
				?.let { it as? Map<*, *> }
				?.get("projects")
				?.let { it as? List<*> }
				?.mapNotNull { it?.toString() }
			?: emptyList()
			ProjectContext.set(projects)
			chain.doFilter(request, response)
		} finally {
			ProjectContext.clear()
		}
	}
}
