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

-- Insert workspace, project and instances in various states for expired instance cleaner tests
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location,  created_at)
VALUES ('ws-001', 'ws-001', NULL, 'Test Workspace', NULL, NULL, '2025-01-01T10:00:00Z');

INSERT INTO projects (project_id, origin_project_id, project_name, project_label, workspace_id, created_at, origin_created_at, attributes)
VALUES ('proj-001', NULL, 'Test Project', 'Label 1', 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}');

INSERT INTO project_instances (instance_id, project_id, instance_name, status, started_at, finished_at, expiring_at, expired_at)
VALUES
    ('inst-active', 'proj-001', 'active.example.com', 'ACTIVE', '2025-06-01T10:00:00Z', NULL, NULL, NULL),
    ('inst-finished', 'proj-001', 'finished.example.com', 'FINISHED', '2025-06-01T10:00:00Z', '2025-06-02T10:00:00Z', NULL, NULL),
    ('inst-expired-old', 'proj-001', 'expired-old.example.com', 'EXPIRED', '2025-05-01T10:00:00Z', '2025-05-02T10:00:00Z', '2025-05-08T10:00:00Z', '2025-05-09T10:00:00Z'),
    ('inst-expired-recent', 'proj-001', 'expired-recent.example.com', 'EXPIRED', '2025-06-10T10:00:00Z', '2025-06-11T10:00:00Z', '2025-06-14T10:00:00Z', '2025-06-14T10:00:00Z');
