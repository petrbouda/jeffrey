-- Fixture for JdbcTraceRepositoryTest.OperationIntervals, layered on top of insert-trace-spans.sql.
--
-- A second span of the slow trace on the pool thread, well after the first one ended: the thread
-- worked on the trace at +60..+80ms and again at +100..+110ms, and did something unrelated in
-- between. An interval reduction that collapses a thread to MIN/MAX would hand the flamegraph one
-- window spanning the idle gap, absorbing that unrelated work; merging with gaps preserved keeps
-- the two stints apart.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.TraceSpan', '2025-01-15T10:00:00.100Z', 100, 10000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":9223372036854775807,"spanId":555,"parentSpanId":111,"name":"flamegraph.recluster","kind":"INTERNAL","status":"UNSET"}');
