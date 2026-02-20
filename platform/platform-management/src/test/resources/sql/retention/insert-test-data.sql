-- Insert workspace and project for retention tests
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

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

-- Old messages (60 days old)
INSERT INTO messages (id, project_id, session_id, type, title, message, severity, category, source, created_at)
VALUES ('msg-old-1', 'proj-001', 'sess-001', 'INFO', 'Old Message 1', 'This is old', 'INFO', 'GENERAL', 'SYSTEM', '2025-05-01T10:00:00Z');
INSERT INTO messages (id, project_id, session_id, type, title, message, severity, category, source, created_at)
VALUES ('msg-old-2', 'proj-001', 'sess-001', 'INFO', 'Old Message 2', 'This is old too', 'INFO', 'GENERAL', 'SYSTEM', '2025-05-05T10:00:00Z');

-- Recent messages (5 days old)
INSERT INTO messages (id, project_id, session_id, type, title, message, severity, category, source, created_at)
VALUES ('msg-recent-1', 'proj-001', 'sess-001', 'INFO', 'Recent Message', 'This is recent', 'INFO', 'GENERAL', 'SYSTEM', '2025-06-25T10:00:00Z');

-- Old alerts (60 days old)
INSERT INTO alerts (id, project_id, session_id, type, title, message, severity, category, source, created_at)
VALUES ('alert-old-1', 'proj-001', 'sess-001', 'WARNING', 'Old Alert 1', 'This is old', 'WARNING', 'GENERAL', 'SYSTEM', '2025-05-01T10:00:00Z');
INSERT INTO alerts (id, project_id, session_id, type, title, message, severity, category, source, created_at)
VALUES ('alert-old-2', 'proj-001', 'sess-001', 'WARNING', 'Old Alert 2', 'This is old too', 'WARNING', 'GENERAL', 'SYSTEM', '2025-05-05T10:00:00Z');

-- Recent alerts (5 days old)
INSERT INTO alerts (id, project_id, session_id, type, title, message, severity, category, source, created_at)
VALUES ('alert-recent-1', 'proj-001', 'sess-001', 'WARNING', 'Recent Alert', 'This is recent', 'WARNING', 'GENERAL', 'SYSTEM', '2025-06-25T10:00:00Z');
