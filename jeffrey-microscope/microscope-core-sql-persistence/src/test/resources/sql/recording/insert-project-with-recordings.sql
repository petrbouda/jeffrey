-- Insert recordings for testing JdbcProjectRecordingRepository
INSERT INTO recording_groups (id, project_id, name, created_at)
VALUES ('group-001', 'proj-001', 'Test Group', '2025-01-01T10:00:00Z');

INSERT INTO recordings (id, project_id, recording_name, group_id, event_source, created_at, recording_started_at, recording_finished_at)
VALUES
    ('rec-001', 'proj-001', 'Recording One', NULL, 'JDK', '2025-01-01T12:00:00Z', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z'),
    ('rec-002', 'proj-001', 'Recording Two', 'group-001', 'ASYNC_PROFILER', '2025-01-01T13:00:00Z', '2025-01-01T12:00:00Z', '2025-01-01T12:30:00Z');

INSERT INTO recording_files (id, project_id, recording_id, filename, supported_type, uploaded_at, size_in_bytes)
VALUES
    ('file-001', 'proj-001', 'rec-001', 'recording1.jfr', 'JFR', '2025-01-01T12:00:00Z', 1024),
    ('file-002', 'proj-001', 'rec-002', 'recording2.jfr', 'JFR', '2025-01-01T13:00:00Z', 2048);
