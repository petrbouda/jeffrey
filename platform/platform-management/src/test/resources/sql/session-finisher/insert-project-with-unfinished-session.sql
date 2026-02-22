-- Insert workspace, project, repository, instance and unfinished session for session finisher tests
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO repositories (project_id, repository_id, repository_type, workspaces_path, relative_workspace_path, relative_project_path)
VALUES ('proj-001', 'repo-001', 'ASYNC_PROFILER', '/workspaces', 'ws-001', 'proj-001');

INSERT INTO project_instances (instance_id, project_id, hostname, started_at)
VALUES ('inst-001', 'proj-001', 'host-1.example.com', '2025-06-15T08:00:00Z');

INSERT INTO project_instance_sessions (session_id, repository_id, instance_id, session_order, relative_session_path, profiler_settings, origin_created_at, created_at, finished_at, last_heartbeat_at)
VALUES ('session-001', 'repo-001', 'inst-001', 1, 'session-2025-06-15', 'cpu=true', '2025-06-15T08:00:00Z', '2025-06-15T08:00:01Z', NULL, NULL);
