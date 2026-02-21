-- Insert a deleted workspace with origin ID
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-deleted', 'origin-ws-deleted', NULL, 'Deleted Workspace', 'A deleted workspace', NULL, NULL, true, '2025-01-01T10:00:00Z', 'LIVE');
