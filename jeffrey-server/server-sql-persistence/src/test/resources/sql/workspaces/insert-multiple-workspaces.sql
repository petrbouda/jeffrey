-- Insert multiple test workspaces
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location, created_at)
VALUES
    ('ws-001', 'ws-001', NULL, 'Workspace One', NULL, NULL, '2025-01-01T10:00:00Z'),
    ('ws-002', 'ws-002', NULL, 'Workspace Two', NULL, NULL, '2025-01-02T10:00:00Z');
