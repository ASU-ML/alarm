ALTER TABLE approvals ADD COLUMN IF NOT EXISTS project_id UUID;
CREATE INDEX IF NOT EXISTS approvals_project_id_idx ON approvals(project_id);
