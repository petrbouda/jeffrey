-- Configuration values at each scope, for testing JdbcScopedConfigRepository
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location,  created_at)
VALUES ('ws-001', 'ws-001', NULL, 'Test Workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}');

INSERT INTO scoped_configs (scope, workspace_id, project_id, config_type, entry_key, config_value, updated_at)
VALUES
    ('GLOBAL', NULL, NULL, 'ASPROF_SETTINGS', '::ASPROF_SETTINGS', 'global-settings', '2025-01-01T12:00:00Z'),
    ('WORKSPACE', 'ws-001', NULL, 'ASPROF_SETTINGS', 'ws-001::ASPROF_SETTINGS', 'workspace-settings', '2025-01-01T12:00:00Z'),
    ('PROJECT', 'ws-001', 'proj-001', 'ASPROF_SETTINGS', 'ws-001:proj-001:ASPROF_SETTINGS', 'project-settings', '2025-01-01T12:00:00Z');
