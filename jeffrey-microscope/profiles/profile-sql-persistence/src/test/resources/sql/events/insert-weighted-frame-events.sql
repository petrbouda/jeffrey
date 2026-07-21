-- Weighted stack-based events used to contrast per-second bucketing (FRAME_BASED) against
-- per-event streaming (FRAME_BASED_EVENTS). Two stacks, with several events landing in the SAME
-- second on the SAME stack so the bucketed query collapses them while the per-event query does not.

-- Frames
INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (201, 'com.example.Alloc', 'newArray', 'JIT', 10, 0),
    (202, 'com.example.Alloc', 'newBuffer', 'JIT', 20, 0),
    (205, 'java.lang.Thread', 'run', 'JIT', 5, 0);

-- Threads
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES (3001, 'worker-1', 22345, 1, false);

-- Event type: an allocation-style, weight-carrying, stack-based type.
INSERT INTO event_types (name, label, type_id, description, categories, source, has_stacktrace, columns)
VALUES ('alloc', 'alloc', 1, 'Allocation samples', '[]', '4', true, '[]');

-- Stacktraces
INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (4001, 1, [201, 205], []),
    (4002, 1, [202, 205], []);

-- Events. weight carries the metric (bytes); samples=1 per event as an OTLP weight import would produce.
-- Stack 4001: three events within second 0 (ms offsets 100, 500, 900); weights 10, 20, 30.
-- Stack 4002: two events within second 1 (ms offsets 1100, 1200); weights 40, 50.
INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('alloc', '2025-01-15T10:00:00Z', 100,  0, 1, 10, 'byte[]', 4001, 3001, NULL),
    ('alloc', '2025-01-15T10:00:00Z', 500,  0, 1, 20, 'byte[]', 4001, 3001, NULL),
    ('alloc', '2025-01-15T10:00:00Z', 900,  0, 1, 30, 'byte[]', 4001, 3001, NULL),
    ('alloc', '2025-01-15T10:00:01Z', 1100, 0, 1, 40, 'byte[]', 4002, 3001, NULL),
    ('alloc', '2025-01-15T10:00:01Z', 1200, 0, 1, 50, 'byte[]', 4002, 3001, NULL);
