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

-- Fixture for JdbcProfileEventRepositoryTest.GetAllFlagsMethod
-- One unchanged boolean flag and one long flag whose value changed during the recording,
-- so the change_history aggregation (epoch-millis timestamps) is exercised.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.BooleanFlag', 'Boolean Flag', 1, 'Boolean JVM flag', '["JVM"]', '1', NULL, false, NULL, NULL, NULL),
    ('jdk.LongFlag', 'Long Flag', 2, 'Long JVM flag', '["JVM"]', '1', NULL, false, NULL, NULL, NULL);

-- 2025-01-15T10:00:00Z = 1736935200000 epoch millis, 2025-01-15T10:05:00Z = 1736935500000.
INSERT INTO events_raw (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.BooleanFlag', '2025-01-15T10:00:00Z', NULL, 1, NULL, NULL, NULL, NULL, '{"name": "UseG1GC", "value": "true", "origin": "Default"}'),
    ('jdk.LongFlag',    '2025-01-15T10:00:00Z', NULL, 1, NULL, NULL, NULL, NULL, '{"name": "MaxHeapSize", "value": "1073741824", "origin": "Default"}'),
    ('jdk.LongFlag',    '2025-01-15T10:05:00Z', NULL, 1, NULL, NULL, NULL, NULL, '{"name": "MaxHeapSize", "value": "2147483648", "origin": "Management"}');
