-- Insert a workspace and projects for testing
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location,  created_at)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, namespace, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES
    ('proj-001', NULL, 'Project One', 'Label 1', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}'),
    ('proj-002', 'origin-002', 'Project Two', NULL, NULL, 'ws-001', '2025-01-01T12:00:00Z', '2025-01-01T10:00:00Z', '{"key":"value"}', '{}');
