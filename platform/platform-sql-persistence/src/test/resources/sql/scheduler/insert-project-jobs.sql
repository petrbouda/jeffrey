-- Insert project scheduler jobs for testing JdbcProjectSchedulerRepository
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO schedulers (id, project_id, job_type, params, enabled)
VALUES
    ('job-001', 'proj-001', 'REPOSITORY_SESSION_CLEANER', '{"keepLast":10}', true),
    ('job-002', 'proj-001', 'REPOSITORY_RECORDING_CLEANER', '{}', false);
