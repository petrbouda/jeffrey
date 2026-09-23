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

-- Insert multiple test workspaces
INSERT INTO workspaces (workspace_id, reference_id, repository_id, name, location, base_location, created_at)
VALUES
    ('ws-001', 'ws-001', NULL, 'Workspace One', NULL, NULL, '2025-01-01T10:00:00Z'),
    ('ws-002', 'ws-002', NULL, 'Workspace Two', NULL, NULL, '2025-01-02T10:00:00Z');
