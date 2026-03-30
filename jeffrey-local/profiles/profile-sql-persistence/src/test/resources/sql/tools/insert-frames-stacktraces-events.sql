-- Frames: various class/method combinations
INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (101, 'com.example.app.UserService', 'getUser', 'JIT', 42, 10),
    (102, 'com.example.app.UserService', 'saveUser', 'JIT', 55, 20),
    (103, 'com.example.app.OrderService', 'createOrder', 'JIT', 30, 5),
    (104, 'com.example.db.ConnectionPool', 'getConnection', 'Interpreted', 100, 0),
    (105, 'java.lang.Thread', 'run', 'JIT', 10, 0),
    (106, 'com.example.app.OrderService', 'cancelOrder', 'JIT', 80, 15);

-- Threads
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (1001, 'main', 12345, 1, false),
    (1002, 'worker-1', 12346, 2, false),
    (1003, 'worker-2', 12347, 3, false);

-- Event types
INSERT INTO event_types (name, label, type_id, description, categories, source, has_stacktrace, columns)
VALUES ('jdk.ExecutionSample', 'Execution Sample', 1, 'CPU execution sample', '["Profiling"]', '1', true, '[]');

-- Stacktraces referencing frames
-- ST 2001: UserService.getUser -> ConnectionPool.getConnection -> Thread.run
INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES (2001, 1, [101, 104, 105], [1]);

-- ST 2002: UserService.saveUser -> ConnectionPool.getConnection -> Thread.run
INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES (2002, 1, [102, 104, 105], [1]);

-- ST 2003: OrderService.createOrder -> Thread.run
INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES (2003, 1, [103, 105], [2]);

-- ST 2004: OrderService.cancelOrder -> ConnectionPool.getConnection -> Thread.run
INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES (2004, 1, [106, 104, 105], [2]);

-- Events referencing stacktraces and threads
INSERT INTO events (event_type, start_timestamp, duration, samples, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.ExecutionSample', '2025-01-15T10:00:00Z', 1000000, 1, 2001, 1001, NULL),
    ('jdk.ExecutionSample', '2025-01-15T10:00:01Z', 1000000, 1, 2001, 1001, NULL),
    ('jdk.ExecutionSample', '2025-01-15T10:00:02Z', 1000000, 1, 2002, 1002, NULL),
    ('jdk.ExecutionSample', '2025-01-15T10:00:03Z', 1000000, 1, 2003, 1002, NULL),
    ('jdk.ExecutionSample', '2025-01-15T10:00:04Z', 1000000, 1, 2003, 1003, NULL),
    ('jdk.ExecutionSample', '2025-01-15T10:00:05Z', 1000000, 1, 2004, 1003, NULL);
