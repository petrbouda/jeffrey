-- Insert a workspace with projects for testing JdbcWorkspaceRepository
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location,  created_at)
VALUES ('ws-001', 'ws-001', NULL, 'Test Workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES
    ('proj-001', NULL, 'Project One', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}'),
    ('proj-002', NULL, 'Project Two', NULL, 'ws-001', '2025-01-01T12:00:00Z', NULL, '{}', '{}');
