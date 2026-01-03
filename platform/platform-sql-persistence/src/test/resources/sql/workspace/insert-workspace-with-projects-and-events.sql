-- Insert a workspace with projects and events for testing JdbcWorkspaceRepository
INSERT INTO workspaces (workspace_id, workspace_origin_id, repository_id, name, description, location, base_location, deleted, created_at, type)
VALUES ('ws-001', NULL, NULL, 'Test Workspace', 'A test workspace', NULL, NULL, false, '2025-01-01T10:00:00Z', 'LIVE');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
VALUES
    ('proj-001', NULL, 'Project One', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}'),
    ('proj-002', NULL, 'Project Two', NULL, 'ws-001', '2025-01-01T12:00:00Z', NULL, '{}', '{}');

INSERT INTO workspace_events (event_id, origin_event_id, project_id, workspace_id, event_type, content, origin_created_at, created_at, created_by)
VALUES
    (1000000000000001, 'origin-event-001', 'proj-001', 'ws-001', 'PROJECT_CREATED', '{"name":"Project One"}', '2025-01-01T11:00:00Z', '2025-01-01T11:00:01Z', 'system'),
    (1000000000000002, 'origin-event-002', 'proj-002', 'ws-001', 'PROJECT_CREATED', '{"name":"Project Two"}', '2025-01-01T12:00:00Z', '2025-01-01T12:00:01Z', 'system');
