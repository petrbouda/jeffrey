-- Fixture for DuckDBFlamegraphSpanFilterTest.
-- jdk.ExecutionSample events across three threads, to prove the span-scope predicate keeps only the
-- samples taken on a span's own thread within its window.
--
-- Span windows under test (absolute epoch millis are computed in the test):
--   span A: thread 2001, [10:00:00.000 .. 10:00:00.300]
--   span B: thread 2002, [10:00:02.000 .. 10:00:02.200]

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.ExecutionSample', 'Execution Sample', 2, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL);

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (2001, 'http-nio-exec-3', 41, 12, false),
    (2002, 'hprof-indexer', 71, 33, false),
    (9999, 'GC Thread#0', 99, 0, false);

INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (9001, 'com.example.Service', 'handle', 'Interpreted', 10, 0);

INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (5001, 1, [9001], []),
    (5002, 1, [9001], []);

INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- KEPT: thread 2001 inside span A
    ('jdk.ExecutionSample', '2025-01-15T10:00:00.050Z', NULL, 1, NULL, NULL, 5001, 2001, '{}'),
    -- EXCLUDED: thread 2001 outside any span window (the idle gap)
    ('jdk.ExecutionSample', '2025-01-15T10:00:09.000Z', NULL, 1, NULL, NULL, 5001, 2001, '{}'),
    -- EXCLUDED: GC thread active during span A's window but not a span thread
    ('jdk.ExecutionSample', '2025-01-15T10:00:00.060Z', NULL, 1, NULL, NULL, 5002, 9999, '{}'),
    -- KEPT: thread 2002 inside span B
    ('jdk.ExecutionSample', '2025-01-15T10:00:02.050Z', NULL, 1, NULL, NULL, 5001, 2002, '{}');
