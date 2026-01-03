-- Insert a workspace, project and recordings for testing JdbcProjectRecordingRepository
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO recording_folders (project_id, id, name)
VALUES ('proj-001', 'folder-001', 'Test Folder');

INSERT INTO recordings (project_id, id, recording_name, folder_id, event_source, created_at, recording_started_at, recording_finished_at)
VALUES
    ('proj-001', 'rec-001', 'Recording One', NULL, 'JDK', '2025-01-01T12:00:00Z', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z'),
    ('proj-001', 'rec-002', 'Recording Two', 'folder-001', 'ASYNC_PROFILER', '2025-01-01T13:00:00Z', '2025-01-01T12:00:00Z', '2025-01-01T12:30:00Z');

INSERT INTO recording_files (project_id, recording_id, id, filename, supported_type, uploaded_at, size_in_bytes)
VALUES
    ('proj-001', 'rec-001', 'file-001', 'recording1.jfr', 'JFR', '2025-01-01T12:00:00Z', 1024),
    ('proj-001', 'rec-002', 'file-002', 'recording2.jfr', 'JFR', '2025-01-01T13:00:00Z', 2048);
