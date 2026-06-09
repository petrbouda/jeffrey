-- Fixture for JdbcSpanRepositoryTest.eventsForThread
-- Mixed events: in-window same-thread (kept), a profiler.Span (excluded), out-of-window (excluded),
-- and another thread in-window (excluded).

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.ExecutionSample', 'Execution Sample', 1, 'CPU sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL),
    ('jdk.JavaMonitorEnter', 'Java Monitor Enter', 2, 'Monitor enter', '["Java Application"]', '1', NULL, true, NULL, NULL, NULL),
    ('profiler.Span', 'Span', 3, 'async-profiler span', '["Profiler"]', '1', NULL, false, NULL, NULL, NULL);

-- vt-worker is a VIRTUAL thread: os_id is NULL (the JVM reports -1 for virtual threads). Pairing
-- must therefore key on thread_hash, not os_id — os_id-based matching can never resolve this thread.
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (3001, 'worker', 91, 5, false),
    (3002, 'other', 92, 6, false),
    (3003, 'vt-worker', NULL, 7, true);

INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- thread 91, inside the [00:00:00.500, 00:00:05.000] window → KEPT
    ('jdk.ExecutionSample', '2026-01-01T00:00:01.000Z',       NULL, 1, NULL, NULL, NULL, 3001, '{"state":"RUNNABLE"}'),
    ('jdk.JavaMonitorEnter', '2026-01-01T00:00:01.500Z',  3000000, 1, NULL, NULL, NULL, 3001, '{"monitorClass":"X"}'),
    ('jdk.ExecutionSample', '2026-01-01T00:00:02.000Z',       NULL, 1, NULL, NULL, NULL, 3001, '{"state":"RUNNABLE"}'),
    -- profiler.Span on the same thread in the window → EXCLUDED (it's the span type itself)
    ('profiler.Span',       '2026-01-01T00:00:01.200Z', 50000000, 1, NULL, NULL, NULL, 3001, '{"tag":"work"}'),
    -- same thread but out of window → EXCLUDED
    ('jdk.ExecutionSample', '2026-01-01T00:00:10.000Z',       NULL, 1, NULL, NULL, NULL, 3001, '{"state":"RUNNABLE"}'),
    -- other thread, in window → EXCLUDED
    ('jdk.ExecutionSample', '2026-01-01T00:00:01.300Z',       NULL, 1, NULL, NULL, NULL, 3002, '{"state":"RUNNABLE"}'),
    -- VIRTUAL thread (os_id NULL), in window → KEPT only because we match by thread_hash
    ('jdk.ExecutionSample', '2026-01-01T00:00:01.800Z',       NULL, 1, NULL, NULL, NULL, 3003, '{"state":"RUNNABLE"}');
