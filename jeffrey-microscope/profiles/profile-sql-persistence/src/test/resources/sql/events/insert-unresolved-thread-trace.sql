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

-- A trace whose only span ran on a thread the recording never described: `events.thread_hash`
-- points at a row that does not exist in `threads`.
--
-- Standalone, not layered on another fixture, so nothing else in the profile can supply a platform
-- thread and mask what is being asserted: not knowing where a span ran is not evidence that a
-- sample could be attributed to it.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.TraceSpan', 'Trace Span', 1, 'trace span', '["Application","Tracing"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"}]');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.TraceSpan', '2025-01-15T10:00:00.000Z', 0, 12000000, 1, NULL, NULL, NULL, 9999,
     '{"traceId":7001,"spanId":701,"parentSpanId":0,"name":"orphan.work","kind":"INTERNAL","status":"UNSET"}');
