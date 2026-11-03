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

-- Fixture for JdbcTraceRepositoryTest.PayloadKeys, layered on insert-trace-spans.sql (which
-- registers jeffrey.JdbcQuery as a span type and creates thread 3001).
--
-- Two statements whose payloads -- what is left of `fields` once the span shape is stripped --
-- differ only in the top byte of two eight-byte words: the 's' of "orders" and the 'i' of "id",
-- eight bytes apart. DuckDB's hash() folds a VARCHAR eight bytes at a time with a xor and a
-- multiply, which carries bits only upwards, so it maps these two texts to the same value. Keyed
-- by that hash, the second payload failed the trace_span_payloads primary key and with it the
-- whole profile initialization.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.JdbcQuery', '2025-01-15T13:00:00.000Z', 10800000, 1000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9400,"spanId":801,"parentSpanId":0,"name":"q","kind":"CLIENT","status":"UNSET","group":"G","sql":"SELECT * FROM orderb WHERE fd = 1","rows":1}'),
    ('jeffrey.JdbcQuery', '2025-01-15T13:00:01.000Z', 10801000, 1000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9401,"spanId":802,"parentSpanId":0,"name":"q","kind":"CLIENT","status":"UNSET","group":"G","sql":"SELECT * FROM ordere WHERE ad = 1","rows":1}');
