-- Insert workspace and project for message/alert tests
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location,  created_at)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, namespace, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');
