-- Insert a workspace, project, repository and sessions for testing
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO repositories (project_id, repository_id, repository_type, workspaces_path, relative_workspace_path, relative_project_path)
VALUES ('proj-001', 'repo-001', 'ASYNC_PROFILER', '/workspaces', 'ws-001', 'proj-001');

INSERT INTO project_instance_sessions (session_id, repository_id, instance_id, session_order, relative_session_path, profiler_settings, finished_file, streaming_enabled, origin_created_at, created_at, finished_at)
VALUES
    ('session-001', 'repo-001', 'inst-001', 1, 'session-2025-01-01', 'cpu=true', 'finished.flag', false, '2025-01-01T12:00:00Z', '2025-01-01T12:00:01Z', '2025-01-01T13:00:00Z'),
    ('session-002', 'repo-001', 'inst-002', 1, 'session-2025-01-02', 'cpu=true,alloc=true', NULL, true, '2025-01-02T12:00:00Z', '2025-01-02T12:00:01Z', NULL);
