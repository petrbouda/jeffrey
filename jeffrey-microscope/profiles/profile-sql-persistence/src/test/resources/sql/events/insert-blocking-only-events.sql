-- Fixture for JdbcTraceRepositoryTest.SpanEventTypeDetection: an ordinary profiling recording.
--
-- Deliberately stands alone rather than layering on insert-trace-spans.sql, because the whole point
-- is the absence of a span-carrying event type. It registers only blocking JDK event types -- none
-- of which declares a spanId field -- and gives them events on a real thread, so the derivation has
-- every blocking event it would want to promote and still nothing to parent them to.
--
-- This is the shape of the overwhelming majority of recordings: JFR from a JVM that carries no
-- Jeffrey span instrumentation at all.

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (4001, 'pool-2-thread-1', 61, 31, false);

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.SocketRead', 'Socket Read', 30, 'socket read', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"host","header":"Remote Host"},{"field":"bytesRead","header":"Bytes Read"}]'),
    ('jdk.JavaMonitorEnter', 'Java Monitor Blocked', 33, 'monitor', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"monitorClass","header":"Monitor Class"}]'),
    ('jdk.ExecutionSample', 'Execution Sample', 40, 'sample', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"state","header":"Thread State"}]');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.SocketRead', '2025-01-15T12:00:00.000Z', 7200000, 5000000, 1, NULL, NULL, NULL, 4001,
     '{"host":"db.internal","bytesRead":4096}'),
    ('jdk.JavaMonitorEnter', '2025-01-15T12:00:00.010Z', 7200010, 2000000, 1, NULL, NULL, NULL, 4001,
     '{"monitorClass":"java.lang.Object"}'),
    ('jdk.ExecutionSample', '2025-01-15T12:00:00.020Z', 7200020, NULL, 1, NULL, NULL, NULL, 4001,
     '{"state":"STATE_RUNNABLE"}');
