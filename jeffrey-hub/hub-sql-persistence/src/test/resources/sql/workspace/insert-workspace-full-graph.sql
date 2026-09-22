-- Two workspaces with a full object graph: projects (incl. a soft-deleted one),
-- repositories, instances, sessions, profiler settings on all levels, and queue rows.
-- Used to verify that deleting ws-001 removes everything reachable only through it
-- and leaves ws-002 (and global profiler settings) untouched.
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location, created_at)
VALUES
    ('ws-001', 'ref-001', NULL, 'Workspace One', NULL, NULL, '2025-01-01T10:00:00Z'),
    ('ws-002', 'ref-002', NULL, 'Workspace Two', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes, deleted_at)
VALUES
    ('proj-001', 'origin-001', 'Project One', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', NULL),
    ('proj-002', 'origin-002', 'Project Deleted', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '2025-01-02T11:00:00Z'),
    ('proj-101', 'origin-101', 'Other Workspace Project', NULL, 'ws-002', '2025-01-01T11:00:00Z', NULL, '{}', NULL);

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

INSERT INTO scoped_configs (scope, workspace_id, project_id, config_type, entry_key, config_value, updated_at)
VALUES
    ('GLOBAL', NULL, NULL, 'ASPROF_SETTINGS', '::ASPROF_SETTINGS', 'global-settings', '2025-01-01T12:00:00Z'),
    ('WORKSPACE', 'ws-001', NULL, 'ASPROF_SETTINGS', 'ws-001::ASPROF_SETTINGS', 'workspace-settings', '2025-01-01T12:00:00Z'),
    ('PROJECT', 'ws-001', 'proj-001', 'ASPROF_SETTINGS', 'ws-001:proj-001:ASPROF_SETTINGS', 'project-settings', '2025-01-01T12:00:00Z'),
    ('WORKSPACE', 'ws-002', NULL, 'ASPROF_SETTINGS', 'ws-002::ASPROF_SETTINGS', 'other-workspace-settings', '2025-01-01T12:00:00Z');

