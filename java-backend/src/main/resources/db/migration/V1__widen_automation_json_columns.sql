-- These five columns were created as VARCHAR(255) instead of the JSON type the
-- original Laravel migration (php/database/migrations/2026_04_30_000600_create_automation_tables.php)
-- declares. Any automation with more than ~1 trivial node, or a run with a
-- non-trivial context, overflowed the 255-char limit and failed to save
-- (MySQL runs with STRICT_TRANS_TABLES, so this failed loudly rather than
-- silently truncating — but it still made non-trivial automations unusable).
--
-- Safe to run even if a column is already JSON (re-applying MODIFY ... JSON is a no-op).

ALTER TABLE automations MODIFY nodes JSON NULL;
ALTER TABLE automations MODIFY edges JSON NULL;
ALTER TABLE automations MODIFY trigger_config JSON NULL;
ALTER TABLE automation_runs MODIFY context JSON NULL;
ALTER TABLE automation_run_logs MODIFY output JSON NULL;
