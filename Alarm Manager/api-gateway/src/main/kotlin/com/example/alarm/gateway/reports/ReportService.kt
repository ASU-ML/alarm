package com.example.alarm.gateway.reports

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter

@Service
class ReportService(
	private val jdbc: JdbcTemplate,
	@Value("\${reports.dir:artifacts}") private val reportsDir: String
) {
	fun generateStandard(from: Instant, to: Instant, format: String): Path {
		Files.createDirectories(Path.of(reportsDir))
		return if (format.lowercase() == "xlsx") generateXlsx(from, to) else generatePdf(from, to)
	}

	private fun generateXlsx(from: Instant, to: Instant): Path {
		val wb = XSSFWorkbook()
		val sheet = wb.createSheet("KPI")
		val row0 = sheet.createRow(0)
		row0.createCell(0).setCellValue("Metric")
		row0.createCell(1).setCellValue("Value")
		val count = jdbc.queryForObject("SELECT count(*) FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc BETWEEN ? AND ?", Long::class.java, from, to) ?: 0L
		val row1 = sheet.createRow(1)
		row1.createCell(0).setCellValue("ALM_IN count")
		row1.createCell(1).setCellValue(count.toDouble())
		val fname = "report_kpi_${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}.xlsx".replace(":","-")
		val path = Path.of(reportsDir, fname)
		Files.newOutputStream(path).use { wb.write(it) }
		wb.close()
		return path
	}

	private fun generatePdf(from: Instant, to: Instant): Path {
		val doc = PDDocument()
		val page = PDPage(PDRectangle.A4)
		doc.addPage(page)
		PDPageContentStream(doc, page).use { cs ->
			val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
			val fontReg = PDType1Font(Standard14Fonts.FontName.HELVETICA)
			cs.beginText()
			cs.setFont(fontBold, 14f)
			cs.newLineAtOffset(50f, 780f)
			cs.showText("Standard KPI Report")
			cs.newLineAtOffset(0f, -24f)
			cs.setFont(fontReg, 11f)
			val count = jdbc.queryForObject("SELECT count(*) FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc BETWEEN ? AND ?", Long::class.java, from, to) ?: 0L
			cs.showText("ALM_IN count: $count")
			cs.endText()
		}
		val fname = "report_kpi_${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}.pdf".replace(":","-")
		val path = Path.of(reportsDir, fname)
		doc.save(path.toFile())
		doc.close()
		return path
	}

	fun generateTemplate(title: String, sections: List<Pair<String, List<String>>>, from: Instant, to: Instant, format: String): Path {
		// Белый список допустимых метрик
		val allowed = setOf("alm_in_count", "ack_p95_s", "standing_alarms")
		val values = mutableMapOf<String, Any>()
		// Заполняем значения
		values["alm_in_count"] = jdbc.queryForObject("SELECT count(*) FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc BETWEEN ? AND ?", Long::class.java, from, to) ?: 0L
		values["ack_p95_s"] = jdbc.queryForObject(
			"""
			WITH pairs AS (
				SELECT i.alarm_id, i.ts_utc AS in_ts, (SELECT a.ts_utc FROM alarm_events a WHERE a.alarm_id=i.alarm_id AND a.event_type='ACK' AND a.ts_utc>=i.ts_utc ORDER BY a.ts_utc ASC LIMIT 1) AS ack_ts
				FROM alarm_events i WHERE i.event_type='ALM_IN' AND i.ts_utc BETWEEN ? AND ?
			)
			SELECT COALESCE(percentile_disc(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (ack_ts - in_ts))),0)
			FROM pairs WHERE ack_ts IS NOT NULL
			""".trimIndent(), Double::class.java, from, to
		) ?: 0.0
		values["standing_alarms"] = jdbc.queryForObject(
			"SELECT count(DISTINCT alarm_id) FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc<=? AND alarm_id NOT IN (SELECT alarm_id FROM alarm_events WHERE event_type='RTN' AND ts_utc BETWEEN ? AND ?)",
			Int::class.java, to, from, to
		) ?: 0

		Files.createDirectories(Path.of(reportsDir))
		val fname = "report_custom_${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}.${if (format.lowercase()=="xlsx") "xlsx" else "pdf"}".replace(":","-")
		val path = Path.of(reportsDir, fname)
		if (format.lowercase()=="xlsx") {
			val wb = XSSFWorkbook()
			sections.forEachIndexed { si, (secTitle, metrics) ->
				val sheet = wb.createSheet("S${si+1}")
				val row0 = sheet.createRow(0)
				row0.createCell(0).setCellValue(secTitle)
				metrics.filter { allowed.contains(it) }.forEachIndexed { i, m ->
					val row = sheet.createRow(i+1)
					row.createCell(0).setCellValue(m)
					row.createCell(1).setCellValue((values[m] ?: "").toString())
				}
			}
			Files.newOutputStream(path).use { wb.write(it) }
			wb.close()
			return path
		} else {
			val doc = PDDocument()
			val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
			val fontReg = PDType1Font(Standard14Fonts.FontName.HELVETICA)
			sections.forEachIndexed { si, (secTitle, metrics) ->
				val page = PDPage(PDRectangle.A4)
				doc.addPage(page)
				PDPageContentStream(doc, page).use { cs ->
					cs.beginText(); cs.setFont(fontBold, 14f); cs.newLineAtOffset(50f, 780f); cs.showText("$title - $secTitle"); cs.newLineAtOffset(0f, -24f); cs.setFont(fontReg, 11f)
					metrics.filter { allowed.contains(it) }.forEach { m -> cs.showText("$m: ${(values[m] ?: "")} "); cs.newLineAtOffset(0f, -16f) }
					cs.endText()
				}
			}
			doc.save(path.toFile()); doc.close(); return path
		}
	}
}
