package com.example.alarm.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.util.*

@Configuration
@EnableMethodSecurity
class SecurityConfig {
	@Bean
	fun filterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { it.disable() }
			.headers { headers ->
				headers.httpStrictTransportSecurity { hsts -> hsts.includeSubDomains(true).preload(true) }
				headers.contentSecurityPolicy { csp -> csp.policyDirectives("default-src 'self'") }
			}
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests { auth ->
				auth
					.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
					.anyRequest().authenticated()
			}
			.oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
			.cors { it.configurationSource(corsConfig()) }
		return http.build()
	}

	@Bean
	fun corsConfig(): UrlBasedCorsConfigurationSource {
		val cfg = CorsConfiguration()
		val origins = System.getenv("CORS_ALLOWED_ORIGINS") ?: "http://localhost:5173"
		cfg.allowedOrigins = Arrays.asList(*origins.split(',').map { it.trim() }.toTypedArray())
		cfg.allowedMethods = listOf("GET","POST","PUT","DELETE")
		cfg.allowedHeaders = listOf("*")
		cfg.allowCredentials = true
		val source = UrlBasedCorsConfigurationSource()
		source.registerCorsConfiguration("/**", cfg)
		return source
	}
}
