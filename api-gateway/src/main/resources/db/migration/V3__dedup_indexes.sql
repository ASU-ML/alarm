-- Индекс для ускорения поиска дублей по источнику и последовательности
CREATE INDEX IF NOT EXISTS alarm_events_dedup ON alarm_events(project_id, source_id, source_seq);
