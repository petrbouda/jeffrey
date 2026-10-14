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

-- Two workspaces with a full object graph: projects (incl. a soft-deleted one),
-- repositories, instances, sessions, and queue rows.
-- Used to verify that deleting ws-001 removes everything reachable only through it
-- and leaves ws-002 untouched.
INSERT INTO workspaces (workspace_id, reference_id, name, created_at)
VALUES
    ('ws-001', 'ref-001', 'Workspace One', '2025-01-01T10:00:00Z'),
    ('ws-002', 'ref-002', 'Workspace Two', '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, workspace_id, created_at, origin_created_at, attributes)
VALUES
    ('proj-001', 'origin-001', 'Project One', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}'),
    ('proj-002', 'origin-002', 'Project Deleted', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}'),
    ('proj-101', 'origin-101', 'Other Workspace Project', 'ws-002', '2025-01-01T11:00:00Z', NULL, '{}');

INSERT INTO repositories (project_id, repository_id, workspaces_path, relative_workspace_path, relative_project_path)
VALUES
    ('proj-001', 'repo-001', NULL, 'ws-001', 'proj-001'),
    ('proj-002', 'repo-002', NULL, 'ws-001', 'proj-002'),
    ('proj-101', 'repo-101', NULL, 'ws-002', 'proj-101');

INSERT INTO project_instances (instance_id, project_id, instance_name, status, started_at)
VALUES
    ('inst-001', 'proj-001', 'instance-1', 'ACTIVE', '2025-01-01T12:00:00Z'),
    ('inst-101', 'proj-101', 'instance-2', 'ACTIVE', '2025-01-01T12:00:00Z');

INSERT INTO project_instance_sessions (session_id, repository_id, instance_id, relative_session_path, origin_created_at, created_at)
VALUES
    ('sess-001', 'repo-001', 'inst-001', 'sessions/sess-001', '2025-01-01T12:00:00Z', '2025-01-01T12:00:00Z'),
    ('sess-101', 'repo-101', 'inst-101', 'sessions/sess-101', '2025-01-01T12:00:00Z', '2025-01-01T12:00:00Z');


