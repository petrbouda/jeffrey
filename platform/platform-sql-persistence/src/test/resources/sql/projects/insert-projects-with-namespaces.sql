-- Insert workspace and projects with various namespaces for testing findAllNamespaces
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, namespace, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES
    ('proj-001', NULL, 'Backend Service', 'Label 1', 'backend', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}'),
    ('proj-002', NULL, 'Frontend App', 'Label 2', 'frontend', 'ws-001', '2025-01-01T12:00:00Z', NULL, '{}', '{}'),
    ('proj-003', NULL, 'No Namespace Project', 'Label 3', NULL, 'ws-001', '2025-01-01T13:00:00Z', NULL, '{}', '{}');
