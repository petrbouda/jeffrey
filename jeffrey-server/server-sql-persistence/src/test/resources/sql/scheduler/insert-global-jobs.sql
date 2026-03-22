-- Insert global scheduler jobs for testing JdbcGlobalSchedulerRepository
INSERT INTO schedulers (id, project_id, job_type, params, enabled)
VALUES
    ('job-001', NULL, 'PROJECTS_SYNCHRONIZER', '{}', true),
    ('job-002', NULL, 'WORKSPACE_EVENTS_REPLICATOR', '{"interval":"30s"}', false);
