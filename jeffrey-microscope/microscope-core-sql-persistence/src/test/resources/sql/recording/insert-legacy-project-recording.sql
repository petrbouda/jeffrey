-- A recording an older build wrote into a project; nothing reads these rows any more
INSERT INTO recording_groups (id, project_id, name, created_at)
VALUES ('legacy-group', 'proj-001', 'Legacy Group', '2025-01-01T10:00:00Z');

INSERT INTO recordings (id, project_id, recording_name, group_id, event_source, created_at, recording_started_at, recording_finished_at)
VALUES ('legacy-rec', 'proj-001', 'Legacy Recording', 'legacy-group', 'JDK', '2025-01-01T12:00:00Z', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z');

INSERT INTO recording_files (id, project_id, recording_id, filename, supported_type, uploaded_at, size_in_bytes)
VALUES ('legacy-file', 'proj-001', 'legacy-rec', 'legacy.jfr', 'JFR', '2025-01-01T12:00:00Z', 1024);
