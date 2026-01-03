-- Insert a single test workspace
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace for testing', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');
