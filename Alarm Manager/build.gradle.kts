import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.9.25" apply false
	id("org.springframework.boot") version "3.3.3" apply false
	id("io.spring.dependency-management") version "1.1.6" apply false
	id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

allprojects {
	group = "com.example.alarm"
	version = "0.1.0-mvp"

	repositories {
		mavenCentral()
	}
}

subprojects {
	apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

configure(listOf(
	project(":api-gateway"),
	project(":alarm-catalog"),
	project(":event-ingest"),
	project(":event-store"),
	project(":kpi-reports"),
	project(":trends"),
	project(":workflow-approvals"),
	project(":access-control"),
	project(":audit-ledger"),
	project(":observability")
)) {
	apply(plugin = "org.jetbrains.kotlin.jvm")
	apply(plugin = "io.spring.dependency-management")

	dependencies {
		"implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.3.3"))
		"implementation"("org.jetbrains.kotlin:kotlin-stdlib")
		"testImplementation"("org.junit.jupiter:junit-jupiter:5.10.3")
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}

	tasks.withType<KotlinCompile> {
		kotlinOptions {
			jvmTarget = "21"
			freeCompilerArgs = listOf("-Xjsr305=strict")
		}
	}
}

listOf(
	"alarm-catalog",
	"event-ingest",
	"event-store",
	"kpi-reports",
	"trends",
	"workflow-approvals",
	"access-control",
	"audit-ledger",
	"observability"
).forEach {
	project(":$it").apply {
		plugins.apply("org.jetbrains.kotlin.jvm")
		plugins.apply("io.spring.dependency-management")
		dependencies {
			"implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.3.3"))
			"implementation"("org.springframework.boot:spring-boot-starter")
			"implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
		}
	}
}
