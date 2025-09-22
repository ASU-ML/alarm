-- Включаем необходимые расширения
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Роли с минимальными привилегиями (IEC 62443)
DO $$ BEGIN
	CREATE ROLE app_read NOINHERIT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
	CREATE ROLE app_write NOINHERIT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
	CREATE ROLE audit_writer NOINHERIT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Базовые таблицы домена
CREATE TABLE IF NOT EXISTS projects (
  project_id UUID PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS alarms (
  alarm_id UUID PRIMARY KEY,
  tag TEXT NOT NULL,
  description TEXT,
  priority SMALLINT CHECK (priority BETWEEN 1 AND 4),
  shelving_allowed BOOLEAN DEFAULT TRUE,
  inhibit_allowed  BOOLEAN DEFAULT TRUE,
  project_id UUID NOT NULL REFERENCES projects(project_id),
  metadata JSONB,
  UNIQUE(project_id, tag)
);

-- Хранилище событий с партиционированием по времени (UTC)
CREATE TABLE IF NOT EXISTS alarm_events (
  ts_utc TIMESTAMPTZ NOT NULL,
  event_type TEXT CHECK (event_type IN ('ALM_IN','ACK','RTN','SHELVE','UNSHELVE','INHIBIT','UNINHIBIT','NOTE')),
  alarm_id UUID NOT NULL REFERENCES alarms(alarm_id),
  value DOUBLE PRECISION,
  operator_id UUID,
  note TEXT,
  source_id TEXT NOT NULL,
  source_seq BIGINT,
  project_id UUID NOT NULL REFERENCES projects(project_id)
) PARTITION BY RANGE (ts_utc);

-- Процедура: обеспечение дневной партиции (можно переключить на месяцы)
CREATE OR REPLACE FUNCTION ensure_alarm_events_partition(p_date date)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  start_ts timestamptz := p_date::timestamptz;
  end_ts   timestamptz := (p_date + 1)::timestamptz;
  part_name text := format('alarm_events_%s', to_char(p_date, 'YYYYMMDD'));
BEGIN
  EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF alarm_events FOR VALUES FROM (%L) TO (%L);', part_name, start_ts, end_ts);
  EXECUTE format('CREATE INDEX IF NOT EXISTS %I_proj_ts ON %I (project_id, ts_utc);', part_name||'_proj_ts', part_name);
  EXECUTE format('CREATE INDEX IF NOT EXISTS %I_alarm_ts ON %I (alarm_id, ts_utc);', part_name||'_alarm_ts', part_name);
  EXECUTE format('CREATE INDEX IF NOT EXISTS %I_event_ts ON %I (event_type, ts_utc);', part_name||'_event_ts', part_name);
END $$;

-- Создаём партиции за сегодня и вчера (для демо)
SELECT ensure_alarm_events_partition((now() at time zone 'utc')::date);
SELECT ensure_alarm_events_partition(((now() at time zone 'utc')::date - 1));

-- Действия оператора
CREATE TABLE IF NOT EXISTS operator_actions (
  action_id UUID PRIMARY KEY,
  ts_utc TIMESTAMPTZ NOT NULL,
  operator_id UUID NOT NULL,
  action_type TEXT NOT NULL,
  payload JSONB,
  project_id UUID NOT NULL REFERENCES projects(project_id),
  workflow_id UUID
);

-- Апрув‑процессы (workflow)
CREATE TABLE IF NOT EXISTS approvals (
  workflow_id UUID PRIMARY KEY,
  state TEXT CHECK (state IN ('draft','pending','approved','rejected')),
  approver_id UUID,
  requested_by UUID NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL,
  decided_at TIMESTAMPTZ,
  reason TEXT,
  signature_hash TEXT
);

-- Неизменяемый аудито‑журнал (append‑only, hash‑chain)
CREATE TABLE IF NOT EXISTS audit_ledger (
  seq BIGSERIAL PRIMARY KEY,
  ts_utc TIMESTAMPTZ NOT NULL,
  actor_id UUID,
  action TEXT NOT NULL,
  object_type TEXT NOT NULL,
  object_id TEXT NOT NULL,
  details JSONB,
  prev_hash TEXT,
  hash TEXT NOT NULL
);

-- Индексы
CREATE INDEX IF NOT EXISTS alarms_proj_tag ON alarms(project_id, tag);
CREATE INDEX IF NOT EXISTS operator_actions_proj_ts ON operator_actions(project_id, ts_utc);
CREATE INDEX IF NOT EXISTS approvals_state_ts ON approvals(state, requested_at);
CREATE INDEX IF NOT EXISTS audit_ledger_ts ON audit_ledger(ts_utc);

-- Включаем RLS и политики доступа по проектам
ALTER TABLE alarms ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE operator_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_ledger ENABLE ROW LEVEL SECURITY;

-- GUC с проектами субъекта (устанавливается приложением в сессии)
DO $$ BEGIN
	PERFORM current_setting('alarm.subject_projects');
EXCEPTION WHEN undefined_object THEN
	PERFORM set_config('alarm.subject_projects', '[]', false);
END $$;

CREATE OR REPLACE FUNCTION subject_has_project(project uuid)
RETURNS boolean LANGUAGE sql AS $$
SELECT project::text = ANY (SELECT jsonb_array_elements_text(current_setting('alarm.subject_projects', true)::jsonb));
$$;

-- Политики
DROP POLICY IF EXISTS rls_alarms_by_project ON alarms;
CREATE POLICY rls_alarms_by_project ON alarms USING (subject_has_project(project_id));

DROP POLICY IF EXISTS rls_events_by_project ON alarm_events;
CREATE POLICY rls_events_by_project ON alarm_events USING (subject_has_project(project_id));

DROP POLICY IF EXISTS rls_actions_by_project ON operator_actions;
CREATE POLICY rls_actions_by_project ON operator_actions USING (subject_has_project(project_id));

DROP POLICY IF EXISTS rls_approvals_by_project ON approvals;
CREATE POLICY rls_approvals_by_project ON approvals USING (true);

-- Аудит: только чтение для аудиторов, INSERT только ролью audit_writer
DROP POLICY IF EXISTS rls_audit_read ON audit_ledger;
CREATE POLICY rls_audit_read ON audit_ledger FOR SELECT TO app_read USING (true);

REVOKE ALL ON audit_ledger FROM PUBLIC;
GRANT INSERT ON audit_ledger TO audit_writer;
GRANT SELECT ON audit_ledger TO app_read;
