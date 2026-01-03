-- Insert profiler settings for testing JdbcProfilerRepository
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO profiler_settings (workspace_id, project_id, agent_settings)
VALUES
    ('$$EMPTY$$', '$$EMPTY$$', 'global-settings'),
    ('ws-001', '$$EMPTY$$', 'workspace-settings'),
    ('ws-001', 'proj-001', 'project-settings');
