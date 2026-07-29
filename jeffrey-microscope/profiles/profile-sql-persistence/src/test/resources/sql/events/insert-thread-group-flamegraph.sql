-- Fixture for DuckDBFlamegraphThreadFilterTest.
-- jdk.ExecutionSample events across a pool of workers plus two threads outside it, to prove a
-- flamegraph opened on a collapsed lane covers every thread in the group and nothing else.
--
-- The pool mixes both kinds of identity on purpose: two workers carry a Java id, one never got one
-- and reports -1, which is how a native thread appears. A group has to match both.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.ExecutionSample', 'Execution Sample', 2, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL);

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (3001, 'oracleApp:connection-adder', 41, 12, false),
    (3002, 'oracleApp:connection-adder', 42, 13, false),
    (3003, 'oracleApp:connection-adder', 43, -1, false),
    (3004, 'main', 44, 1, false),
    (3005, 'GC Thread#0', 45, -1, false);

INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (9001, 'com.example.Pool', 'addConnection', 'Interpreted', 10, 0);

INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (5001, 1, [9001], []);

INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- KEPT by the group: a worker identified by its Java id
    ('jdk.ExecutionSample', '2025-01-15T10:00:00.050Z', NULL, 1, NULL, NULL, 5001, 3001, '{}'),
    -- KEPT by the group: a second worker, so the filter cannot be matching one thread
    ('jdk.ExecutionSample', '2025-01-15T10:00:01.050Z', NULL, 1, NULL, NULL, 5001, 3002, '{}'),
    -- KEPT by the group: a worker with no Java id, matched on its OS id instead
    ('jdk.ExecutionSample', '2025-01-15T10:00:02.050Z', NULL, 1, NULL, NULL, 5001, 3003, '{}'),
    -- EXCLUDED: a thread outside the group
    ('jdk.ExecutionSample', '2025-01-15T10:00:03.050Z', NULL, 1, NULL, NULL, 5001, 3004, '{}'),
    -- EXCLUDED: another thread with no Java id, which must not be swept in by the -1 marker
    ('jdk.ExecutionSample', '2025-01-15T10:00:04.050Z', NULL, 1, NULL, NULL, 5001, 3005, '{}');
