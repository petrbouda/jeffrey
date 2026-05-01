-- Insert event type for ThreadAllocationStatistics
INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.ThreadAllocationStatistics', 'Thread Allocation Statistics', 2, 'Thread allocation stats', '["Memory"]', '1', NULL, false, NULL, NULL, NULL);

-- Insert threads
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (1001, 'main', 12345, 1, false),
    (1002, 'worker-1', 12346, 2, false),
    (1003, 'worker-2', 12347, 3, false),
    (1004, 'http-handler-1', 12348, 4, false);

-- Insert ThreadAllocationStatistics events with different weights
-- Latest timestamp events (these should be returned)
INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.ThreadAllocationStatistics', '2025-01-15T10:00:10Z', NULL, 1, 5000000, NULL, NULL, 1001, NULL),
    ('jdk.ThreadAllocationStatistics', '2025-01-15T10:00:10Z', NULL, 1, 3000000, NULL, NULL, 1002, NULL),
    ('jdk.ThreadAllocationStatistics', '2025-01-15T10:00:10Z', NULL, 1, 1000000, NULL, NULL, 1003, NULL),
    ('jdk.ThreadAllocationStatistics', '2025-01-15T10:00:10Z', NULL, 1, 500000, NULL, NULL, 1004, NULL);

-- Older events (should NOT be returned)
INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.ThreadAllocationStatistics', '2025-01-15T09:00:00Z', NULL, 1, 100000, NULL, NULL, 1001, NULL),
    ('jdk.ThreadAllocationStatistics', '2025-01-15T09:00:00Z', NULL, 1, 200000, NULL, NULL, 1002, NULL);
