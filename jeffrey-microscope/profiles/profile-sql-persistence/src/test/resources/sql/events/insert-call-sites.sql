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

-- Fixture for JdbcCallSitesTest: statements on four stacks, the reader under test charging 5001 and 5002
-- to one path (a site reached through two stacks, so its per-trace figures need the stacks merged
-- first), 5003 to another and 5004 to none.
--
--   path 0, 'SELECT order_lines': trace 7 issues it 3 + 2 times across the two stacks, trace 8 twice,
--                                 trace 9 four times -> traces 3, at most 5, median 4, busiest 7, 9, 8
--   path 0, 'SELECT orders':      twice outside any trace (traceId 0 is the tracer's "no trace")
--   path 1, a 250-character statement pooled into field_texts, once in trace 7
--   unattributed, 'SELECT orders': once in trace 8
--   one jdk.SocketRead on stack 5001, which a read of statements never counts

INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (9101, 'com.acme.orders.OrderRepository', 'linesOf', 'JIT compiled', 140, 0);

INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (5001, 1, [9101], []),
    (5002, 1, [9101], []),
    (5003, 1, [9101], []),
    (5004, 1, [9101], []);

INSERT INTO field_texts (text_hash, text)
VALUES
    (77, repeat('S', 250));

INSERT INTO traces (trace_id, root_name, root_kind, root_event_type, root_span_id, start_timestamp,
                    start_timestamp_from_beginning, duration, span_count, error_count, has_platform_span)
VALUES
    (7, 'GET /orders/{id}', 'SERVER', 'jeffrey.HttpServerExchange', 70, '2025-01-15T10:00:00.000Z', 0, 1000000, 1, 0, true),
    (8, 'GET /orders/{id}', 'SERVER', 'jeffrey.HttpServerExchange', 80, '2025-01-15T10:00:01.000Z', 1000, 1000000, 1, 0, true),
    (9, 'GET /orders/{id}', 'SERVER', 'jeffrey.HttpServerExchange', 90, '2025-01-15T10:00:02.000Z', 2000, 1000000, 1, 0, true);

INSERT INTO events_raw (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields, pooled_field, pooled_text_hash)
VALUES
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.001Z', 100, 1, NULL, NULL, 5001, 1, '{"sql":"SELECT order_lines","traceId":7}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.002Z', 100, 1, NULL, NULL, 5001, 1, '{"sql":"SELECT order_lines","traceId":7}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.003Z', 100, 1, NULL, NULL, 5001, 1, '{"sql":"SELECT order_lines","traceId":7}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.004Z', 100, 1, NULL, NULL, 5002, 1, '{"sql":"SELECT order_lines","traceId":7}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.005Z', 900, 1, NULL, NULL, 5002, 1, '{"sql":"SELECT order_lines","traceId":7}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:01.001Z', 100, 1, NULL, NULL, 5001, 1, '{"sql":"SELECT order_lines","traceId":8}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:01.002Z', 100, 1, NULL, NULL, 5001, 1, '{"sql":"SELECT order_lines","traceId":8}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:02.001Z', 100, 1, NULL, NULL, 5002, 1, '{"sql":"SELECT order_lines","traceId":9}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:02.002Z', 100, 1, NULL, NULL, 5002, 1, '{"sql":"SELECT order_lines","traceId":9}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:02.003Z', 100, 1, NULL, NULL, 5002, 1, '{"sql":"SELECT order_lines","traceId":9}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:02.004Z', 100, 1, NULL, NULL, 5002, 1, '{"sql":"SELECT order_lines","traceId":9}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:03.001Z', 50, 1, NULL, NULL, 5001, 1, '{"sql":"SELECT orders","traceId":0}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:03.002Z', 50, 1, NULL, NULL, 5002, 1, '{"sql":"SELECT orders","traceId":0}', NULL, NULL),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:00.006Z', 300, 1, NULL, NULL, 5003, 1, '{"traceId":7}', 'sql', 77),
    ('jeffrey.JdbcStatement', '2025-01-15T10:00:01.003Z', 70, 1, NULL, NULL, 5004, 1, '{"sql":"SELECT orders","traceId":8}', NULL, NULL),
    ('jdk.SocketRead', '2025-01-15T10:00:00.007Z', 500, 1, NULL, NULL, 5001, 1, '{"traceId":7}', NULL, NULL);
