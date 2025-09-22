pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

rootProject.name = "alarm-manager"

include(
	"api-gateway",
	"alarm-catalog",
	"event-ingest",
	"event-store",
	"kpi-reports",
	"trends",
	"workflow-approvals",
	"access-control",
	"audit-ledger",
	"observability"
)
