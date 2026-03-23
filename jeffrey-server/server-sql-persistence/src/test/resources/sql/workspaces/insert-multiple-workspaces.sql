-- Insert multiple test workspaces
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, created_at)
VALUES
    ('ws-001', NULL, NULL, 'Workspace One', 'First workspace', NULL, NULL, '2025-01-01T10:00:00Z'),
    ('ws-002', NULL, NULL, 'Workspace Two', 'Second workspace', NULL, NULL, '2025-01-02T10:00:00Z');
