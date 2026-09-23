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
-- Two traces with exactly the same duration, so ordering by duration alone cannot decide between
-- them. The slowest-traces list breaks the tie on trace_id; without that, the trace at a LIMIT
-- boundary comes and goes between two identical requests.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:03.000Z', 3000, 7000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7002,"spanId":331,"parentSpanId":0,"name":"GET /tied","kind":"SERVER","status":"UNSET","method":"GET","uri":"/tied","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:04.000Z', 4000, 7000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7001,"spanId":332,"parentSpanId":0,"name":"GET /tied","kind":"SERVER","status":"UNSET","method":"GET","uri":"/tied","statusCode":200}');
