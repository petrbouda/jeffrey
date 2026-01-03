-- Insert multiple test workspaces
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES
    ('ws-001', NULL, NULL, 'Workspace One', 'First workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE'),
    ('ws-002', NULL, NULL, 'Workspace Two', 'Second workspace', NULL, NULL, false, '2025-01-02T10:00:00Z', 'SANDBOX'),
    ('ws-003', NULL, NULL, 'Deleted Workspace', 'This workspace is deleted', NULL, NULL, true, '2025-01-03T10:00:00Z', 'LIVE');
