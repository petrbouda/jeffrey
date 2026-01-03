-- Insert a workspace with an event consumer for testing
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO workspace_event_consumers (consumer_id, workspace_id, last_offset, last_execution_at, created_at)
VALUES ('consumer-001', 'ws-001', 100, '2025-01-01T15:00:00Z', '2025-01-01T10:00:00Z');
