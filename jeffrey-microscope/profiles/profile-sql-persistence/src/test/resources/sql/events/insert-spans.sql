-- Fixture for JdbcSpanRepositoryTest
-- Inserts profiler.Span events on two threads, plus a non-span event to verify type filtering.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('profiler.Span', 'Span', 1, 'async-profiler span', '["Profiler"]', '1', NULL, false, NULL, NULL, NULL),
    ('jdk.ExecutionSample', 'Execution Sample', 2, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL);

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (2001, 'http-nio-exec-3', 41, 12, false),
    (2002, 'hprof-indexer', 71, 33, false);

-- Durations in nanoseconds. First event at 10:00:00.000 establishes the relative-time origin.
INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('profiler.Span',       '2025-01-15T10:00:00.000Z', 300000000, 1, NULL, NULL, NULL, 2001, '{"tag":"profile.initialize"}'),
    ('profiler.Span',       '2025-01-15T10:00:00.100Z', 120000000, 1, NULL, NULL, NULL, 2001, '{"tag":"jfr.parse_and_ingest"}'),
    ('profiler.Span',       '2025-01-15T10:00:02.000Z', 200000000, 1, NULL, NULL, NULL, 2002, '{"tag":"hprof.index.build"}'),
    -- Non-span event: must NOT be returned by listSpans().
    ('jdk.ExecutionSample', '2025-01-15T10:00:00.050Z',      NULL, 1, NULL, NULL, NULL, 2001, '{"state":"RUNNABLE"}');
