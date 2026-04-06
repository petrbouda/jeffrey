-- Insert workspace and project for retention tests
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location,  created_at)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}');

-- Old queue events (60 days old, beyond 31-day retention)
INSERT INTO persistent_queue_events (queue_name, scope_id, payload, created_at)
VALUES ('test-queue', 'ws-001', '{"type":"OLD_EVENT_1"}', '2025-05-01T10:00:00Z');
INSERT INTO persistent_queue_events (queue_name, scope_id, payload, created_at)
VALUES ('test-queue', 'ws-001', '{"type":"OLD_EVENT_2"}', '2025-05-05T10:00:00Z');

-- Recent queue events (5 days old, within 31-day retention)
INSERT INTO persistent_queue_events (queue_name, scope_id, payload, created_at)
VALUES ('test-queue', 'ws-001', '{"type":"RECENT_EVENT_1"}', '2025-06-25T10:00:00Z');

