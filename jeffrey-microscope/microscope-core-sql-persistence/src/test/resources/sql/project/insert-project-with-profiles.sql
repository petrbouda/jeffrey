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

-- Insert recordings and profiles for testing JdbcProfileRepository
INSERT INTO recordings (project_id, id, recording_name, group_id, event_source, created_at, recording_started_at, recording_finished_at)
VALUES ('proj-001', 'rec-001', 'Test Recording', NULL, 'JDK', '2025-01-01T12:00:00Z', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z');

INSERT INTO profiles (profile_id, project_id, workspace_id, profile_name, event_source, created_at, recording_id, recording_started_at, recording_finished_at, enabled_at)
VALUES
    ('profile-001', 'proj-001', 'ws-001', 'Profile One', 'JDK', '2025-01-01T12:00:00Z', 'rec-001', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z', '2025-01-01T12:00:02Z'),
    ('profile-002', 'proj-001', 'ws-001', 'Profile Two', 'JDK', '2025-01-01T13:00:00Z', 'rec-001', '2025-01-01T11:00:00Z', '2025-01-01T11:30:00Z', NULL);
