package com.example.alarm.gateway.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventRepositoryIT {
	companion object {
		class Pg : PostgreSQLContainer<Pg>("timescale/timescaledb:2.16.1-pg16")
	}
	lateinit var jdbc: JdbcTemplate
	lateinit var repo: EventRepository

	@BeforeAll
	fun setup() {
		val pg = Pg().withDatabaseName("alarm").withUsername("alarm").withPassword("alarm")
		pg.start()
		val ds = DataSourceBuilder.create().url(pg.jdbcUrl).username(pg.username).password(pg.password).build()
		jdbc = JdbcTemplate(ds)
		// minimal schema for test
		jdbc.execute("CREATE EXTENSION IF NOT EXISTS timescaledb;")
		jdbc.execute("CREATE TABLE projects(project_id uuid primary key, name text unique not null);")
		jdbc.execute("CREATE TABLE alarms(alarm_id uuid primary key, tag text not null, project_id uuid not null);")
		jdbc.execute("CREATE TABLE alarm_events(ts_utc timestamptz not null, event_type text, alarm_id uuid, value double precision, operator_id uuid, note text, source_id text not null, source_seq bigint, project_id uuid not null);")
		repo = EventRepository(jdbc)
		val projectId = UUID.randomUUID().toString()
		jdbc.update("INSERT INTO projects(project_id,name) VALUES (?,?)", UUID.fromString(projectId), "p1")
		val alarmId = UUID.randomUUID().toString()
		jdbc.update("INSERT INTO alarms(alarm_id,tag,project_id) VALUES (?,?,?)", UUID.fromString(alarmId), "T1", UUID.fromString(projectId))
		repo.insertEvent(NormalizedEvent(Instant.now(), "ALM_IN", alarmId, 1.0, null, null, "s", 1, projectId))
		repo.insertEvent(NormalizedEvent(Instant.now(), "ACK", alarmId, null, null, null, "s", 2, projectId))
	}

	@Test
	fun fetchSinceReturnsEvents() {
		val since = Instant.now().minusSeconds(3600)
		val events = repo.fetchSince(listOf(), since)
		assertEquals(2, events.size)
	}
}
