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

-- Fixture for JdbcTraceRepositoryTest.Reads, layered on top of insert-trace-spans.sql.
--
-- Three traces of one operation whose slowest member started last, so the order they ran in and the
-- order of their durations disagree. A query that took the first N by start time and ranked them
-- would name the 3 ms and the 9 ms trace as the slowest two and never see the 50 ms one.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:05.000Z', 5000, 3000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8001,"spanId":441,"parentSpanId":0,"name":"GET /ranked","kind":"SERVER","status":"UNSET","method":"GET","uri":"/ranked","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:06.000Z', 6000, 9000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8002,"spanId":442,"parentSpanId":0,"name":"GET /ranked","kind":"SERVER","status":"UNSET","method":"GET","uri":"/ranked","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:07.000Z', 7000, 50000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8003,"spanId":443,"parentSpanId":0,"name":"GET /ranked","kind":"SERVER","status":"UNSET","method":"GET","uri":"/ranked","statusCode":200}');
