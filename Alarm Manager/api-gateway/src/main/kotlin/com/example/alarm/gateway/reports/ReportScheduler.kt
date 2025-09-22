package com.example.alarm.gateway.reports

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ReportScheduler(
	private val service: ReportService,
	@Value("\${reports.schedule.enabled:true}") private val enabled: Boolean,
	@Value("\${reports.schedule.format:pdf}") private val format: String
) {
	@Scheduled(cron = "\${reports.schedule.cron:0 5 0 * * *}")
	fun daily() {
		if (!enabled) return
		val now = Instant.now()
		val from = now.minusSeconds(24 * 3600)
		service.generateStandard(from, now, format)
	}
}
