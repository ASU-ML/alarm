package com.example.alarm.gateway.reports

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.ss.usermodel.WorkbookFactory
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
		val rate = jdbc.queryForObject("SELECT count(*) FROM alarm_events WHERE event_type='ALM_IN' AND ts_utc BETWEEN ? AND ?", Long::class.java, from, to) ?: 0L
		val row1 = sheet.createRow(1)
		row1.createCell(0).setCellValue("ALM_IN count")
		row1.createCell(1).setCellValue(rate.toDouble())
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
			cs.beginText()
			cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
			cs.newLineAtOffset(50f, 780f)
			cs.showText("Standard KPI Report")
			cs.newLineAtOffset(0f, -24f)
			cs.setFont(PDType1Font.HELVETICA, 11f)
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
}
