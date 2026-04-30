-- Insert a workspace and project for testing JdbcProjectRepository delete
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location, created_at)
VALUES ('ws-001', 'ws-001', NULL, 'Test Workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

INSERT INTO schedulers (id, project_id, job_type, params, enabled)
VALUES ('job-001', 'proj-001', 'PROJECT_INSTANCE_SESSION_CLEANER', '{"keepLast":10}', true);

INSERT INTO profiler_settings (workspace_id, project_id, agent_settings)
VALUES ('ws-001', 'proj-001', 'start,event=cpu');
