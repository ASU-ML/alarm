import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":alarm-catalog"))
	implementation(project(":event-ingest"))
	implementation(project(":event-store"))
	implementation(project(":kpi-reports"))
	implementation(project(":trends"))
	implementation(project(":workflow-approvals"))
	implementation(project(":access-control"))
	implementation(project(":audit-ledger"))
	implementation(project(":observability"))

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.postgresql:postgresql:42.7.3")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.39.0")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

	// Reports: PDF/XLSX
	implementation("org.apache.pdfbox:pdfbox:3.0.2")
	implementation("org.apache.poi:poi-ooxml:5.2.5")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.testcontainers:junit-jupiter:1.20.1")
	testImplementation("org.testcontainers:postgresql:1.20.1")
	testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
}

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile> {
	kotlinOptions.jvmTarget = "21"
}
