-- Two workspaces with a full object graph: projects (incl. a soft-deleted one),
-- repositories, instances, sessions, profiler settings on all levels, and queue rows.
-- Used to verify that deleting ws-001 removes everything reachable only through it
-- and leaves ws-002 (and global profiler settings) untouched.
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location, created_at)
VALUES
    ('ws-001', 'ref-001', NULL, 'Workspace One', NULL, NULL, '2025-01-01T10:00:00Z'),
    ('ws-002', 'ref-002', NULL, 'Workspace Two', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, graph_visualization, deleted_at)
VALUES
    ('proj-001', 'origin-001', 'Project One', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}', NULL),
    ('proj-002', 'origin-002', 'Project Deleted', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}', '2025-01-02T11:00:00Z'),
    ('proj-101', 'origin-101', 'Other Workspace Project', NULL, 'ws-002', '2025-01-01T11:00:00Z', NULL, '{}', '{}', NULL);

INSERT INTO repositories (project_id, repository_id, repository_type, workspaces_path, relative_workspace_path, relative_project_path)
VALUES
    ('proj-001', 'repo-001', 'ASYNC_PROFILER', NULL, 'ws-001', 'proj-001'),
    ('proj-002', 'repo-002', 'ASYNC_PROFILER', NULL, 'ws-001', 'proj-002'),
    ('proj-101', 'repo-101', 'ASYNC_PROFILER', NULL, 'ws-002', 'proj-101');

INSERT INTO project_instances (instance_id, project_id, instance_name, status, started_at)
VALUES
    ('inst-001', 'proj-001', 'instance-1', 'ACTIVE', '2025-01-01T12:00:00Z'),
    ('inst-101', 'proj-101', 'instance-2', 'ACTIVE', '2025-01-01T12:00:00Z');

INSERT INTO project_instance_sessions (session_id, repository_id, instance_id, session_order, relative_session_path, origin_created_at, created_at)
VALUES
    ('sess-001', 'repo-001', 'inst-001', 1, 'sessions/sess-001', '2025-01-01T12:00:00Z', '2025-01-01T12:00:00Z'),
    ('sess-101', 'repo-101', 'inst-101', 1, 'sessions/sess-101', '2025-01-01T12:00:00Z', '2025-01-01T12:00:00Z');

INSERT INTO profiler_settings (workspace_id, project_id, agent_settings)
VALUES
    (NULL, NULL, 'global-settings'),
    ('ws-001', NULL, 'workspace-settings'),
    (NULL, 'proj-001', 'project-settings'),
    ('ws-002', NULL, 'other-workspace-settings');

INSERT INTO persistent_queue_events (queue_name, scope_id, dedup_key, payload, created_at)
VALUES
    ('workspace-events', 'ws-001', 'dedup-1', '{}', '2025-01-01T13:00:00Z'),
    ('workspace-events', 'ws-002', 'dedup-2', '{}', '2025-01-01T13:00:00Z');

INSERT INTO persistent_queue_consumers (consumer_id, queue_name, scope_id, last_offset, created_at)
VALUES
    ('consumer-1', 'workspace-events', 'ws-001', 0, '2025-01-01T13:00:00Z'),
    ('consumer-1', 'workspace-events', 'ws-002', 0, '2025-01-01T13:00:00Z');
