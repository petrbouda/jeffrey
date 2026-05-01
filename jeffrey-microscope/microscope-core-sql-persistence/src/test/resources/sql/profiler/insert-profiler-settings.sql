-- Insert profiler settings for testing JdbcProfilerRepository
INSERT INTO profiler_settings (workspace_id, project_id, agent_settings)
VALUES
    ('$$EMPTY$$', '$$EMPTY$$', 'global-settings'),
    ('ws-001', '$$EMPTY$$', 'workspace-settings'),
    ('ws-001', 'proj-001', 'project-settings');
