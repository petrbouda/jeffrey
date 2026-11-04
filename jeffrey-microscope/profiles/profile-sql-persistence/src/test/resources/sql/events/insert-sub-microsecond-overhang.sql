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
-- Fixture for JdbcTraceRepositoryTest, layered on top of insert-trace-spans.sql.
--
-- A request whose last step ends with it. A span's start is stored to the microsecond, so the step,
-- which really ended a few hundred nanoseconds before its root, appears to end 500 ns after it:
--   root 10:06:00.000000, 10 000 ns;   step 10:06:00.000009, 1 500 ns -> ends at 10 500 ns.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:06:00.000000Z', 360000, 10000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8101,"spanId":811,"parentSpanId":0,"name":"GET /overhang","kind":"SERVER","status":"UNSET","uri":"/overhang","method":"GET","statusCode":200}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:06:00.000009Z', 360000, 1500, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8101,"spanId":812,"parentSpanId":811,"name":"overhang-step","kind":"INTERNAL","status":"UNSET"}');
