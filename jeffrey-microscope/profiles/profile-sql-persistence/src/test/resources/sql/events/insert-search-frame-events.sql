-- Stack-based events used to verify the searching timeseries query (SIMPLE_SEARCH). Three stacks:
-- one topped by a constructor frame (`JsonReader#<init>`), one by a regular method, and one by a
-- bare native symbol without a class name (concat_ws must still match it on the method alone).

-- Frames
INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (301, 'com.google.gson.stream.JsonReader', '<init>', 'JIT', 10, 0),
    (302, 'com.example.Service', 'process', 'JIT', 20, 0),
    (305, 'java.lang.Thread', 'run', 'JIT', 5, 0),
    (306, NULL, 'clone3', 'NATIVE', 0, 0);

-- Threads
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES (3001, 'worker-1', 22345, 1, false);

-- Event type: an allocation-style, weight-carrying, stack-based type.
INSERT INTO event_types (name, label, type_id, description, categories, source, has_stacktrace, columns)
VALUES ('alloc', 'alloc', 1, 'Allocation samples', '[]', '4', true, '[]');

-- Stacktraces
INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (4101, 1, [301, 305], []),
    (4102, 1, [302, 305], []),
    (4103, 1, [306], []);

-- Events: one per stack, all in second 0.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('alloc', '2025-01-15T10:00:00Z', 100, 0, 1, 10, 'byte[]', 4101, 3001, NULL),
    ('alloc', '2025-01-15T10:00:00Z', 500, 0, 1, 20, 'byte[]', 4102, 3001, NULL),
    ('alloc', '2025-01-15T10:00:00Z', 900, 0, 1, 30, 'byte[]', 4103, 3001, NULL);
