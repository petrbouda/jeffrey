-- Insert workspace, project, FINISHED instance with a single finished session for deletion → EXPIRED tests
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location,  created_at)
VALUES ('ws-001', 'ws-001', NULL, 'Test Workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', 'origin-proj-001', 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO repositories (project_id, repository_id, repository_type, workspaces_path, relative_workspace_path, relative_project_path)
VALUES ('proj-001', 'repo-001', 'ASYNC_PROFILER', '/workspaces', 'ws-001', 'proj-001');

INSERT INTO project_instances (instance_id, project_id, instance_name, status, started_at, finished_at)
VALUES ('inst-001', 'proj-001', 'inst-001', 'FINISHED', '2025-06-15T10:00:00Z', '2025-06-15T11:00:00Z');

INSERT INTO project_instance_sessions (session_id, repository_id, instance_id, session_order, relative_session_path, origin_created_at, created_at, finished_at)
VALUES ('session-001', 'repo-001', 'inst-001', 1, 'session-001', '2025-06-15T10:00:00Z', '2025-06-15T10:00:01Z', '2025-06-15T11:00:00Z');
