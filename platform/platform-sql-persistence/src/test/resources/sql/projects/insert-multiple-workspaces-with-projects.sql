-- Insert multiple workspaces and projects for filtering tests
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES
    ('ws-001', NULL, NULL, 'Workspace One', 'First workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE'),
    ('ws-002', NULL, NULL, 'Workspace Two', 'Second workspace', NULL, NULL, false, '2025-01-02T10:00:00Z', 'SANDBOX');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, namespace, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES
    ('proj-001', NULL, 'Project A in WS1', NULL, NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}'),
    ('proj-002', NULL, 'Project B in WS1', NULL, NULL, 'ws-001', '2025-01-01T12:00:00Z', NULL, '{}', '{}'),
    ('proj-003', NULL, 'Project C in WS2', NULL, NULL, 'ws-002', '2025-01-02T11:00:00Z', NULL, '{}', '{}');
