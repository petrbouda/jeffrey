-- Jeffrey
-- Copyright (C) 2026 Petr Bouda
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Insert a workspace, project, repository and sessions for testing
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location,  created_at)
VALUES ('ws-001', 'ws-001', NULL, 'Test Workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}');

INSERT INTO repositories (project_id, repository_id, repository_type, workspaces_path, relative_workspace_path, relative_project_path)
VALUES ('proj-001', 'repo-001', 'ASYNC_PROFILER', '/workspaces', 'ws-001', 'proj-001');

INSERT INTO project_instance_sessions (session_id, repository_id, instance_id, session_order, relative_session_path, origin_created_at, created_at, finished_at)
VALUES
    ('session-001', 'repo-001', 'inst-001', 1, 'session-2025-01-01', '2025-01-01T12:00:00Z', '2025-01-01T12:00:01Z', '2025-01-01T13:00:00Z'),
    ('session-002', 'repo-001', 'inst-002', 1, 'session-2025-01-02', '2025-01-02T12:00:00Z', '2025-01-02T12:00:01Z', NULL);
