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

-- Fixture for DuckDBFlamegraphSpanFilterTest, on top of insert-span-flamegraph.sql: two samples at the
-- edges of a window's milliseconds, where flooring a timestamp to its millisecond and rounding it differ.
--   span A: thread 2001, [10:00:00.000 .. 10:00:00.300] — the last millisecond is 300, up to 300.999
--   span B: thread 2002, [10:00:02.000 .. 10:00:02.200] — it opens at 2.000

INSERT INTO events_raw (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- KEPT: thread 2001 in span A's last millisecond, which rounds to the millisecond after it
    ('jdk.ExecutionSample', '2025-01-15T10:00:00.300900Z', NULL, 1, NULL, NULL, 5001, 2001, '{}'),
    -- EXCLUDED: thread 2002 four tenths of a millisecond before span B opens, which rounds into it
    ('jdk.ExecutionSample', '2025-01-15T10:00:01.999600Z', NULL, 1, NULL, NULL, 5001, 2002, '{}');
