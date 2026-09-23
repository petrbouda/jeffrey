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

-- Fixture for JdbcTraceRepositoryTest.Derivation, layered on top of insert-trace-spans.sql.
--
-- A second event claiming span 112 of the slow trace -- a re-imported chunk, or third-party
-- instrumentation that reused an id. The derivation must keep exactly one row per (trace, span):
-- the earliest occurrence, which is the row the waterfall would have drawn anyway, so span_count
-- agrees with what the UI renders.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.JdbcQuery', '2025-01-15T10:00:00.015Z', 15, 99000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":112,"parentSpanId":111,"name":"listSpans-duplicate","kind":"CLIENT","status":"UNSET","group":"PROFILE_EVENTS"}');
