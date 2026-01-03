-- Insert a workspace, project with profiles for testing JdbcProjectRepository
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO recordings (project_id, id, recording_name, folder_id, event_source, created_at, recording_started_at, recording_finished_at)
VALUES ('proj-001', 'rec-001', 'Test Recording', NULL, 'JDK', '2025-01-01T12:00:00Z', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z');

INSERT INTO profiles (profile_id, project_id, profile_name, event_source, created_at, recording_id, recording_started_at, recording_finished_at, initialized_at, enabled_at)
VALUES
    ('profile-001', 'proj-001', 'Profile One', 'JDK', '2025-01-01T12:00:00Z', 'rec-001', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z', '2025-01-01T12:00:01Z', '2025-01-01T12:00:02Z'),
    ('profile-002', 'proj-001', 'Profile Two', 'JDK', '2025-01-01T13:00:00Z', 'rec-001', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z', '2025-01-01T13:00:01Z', NULL);
