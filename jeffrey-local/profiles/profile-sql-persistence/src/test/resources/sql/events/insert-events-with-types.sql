-- Insert event types
INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.ExecutionSample', 'Execution Sample', 1, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL, '[{"field":"state","header":"State","type":"string","description":"Thread state"}]'),
    ('jdk.ThreadAllocationStatistics', 'Thread Allocation Statistics', 2, 'Thread allocation stats', '["Memory"]', '1', NULL, false, NULL, NULL, '[]'),
    ('jdk.ObjectAllocationInNewTLAB', 'Allocation in new TLAB', 3, 'Object allocation in new TLAB', '["Memory"]', '1', NULL, true, NULL, NULL, NULL),
    ('jdk.GCPhasePause', 'GC Phase Pause', 4, 'GC pause phase', '["GC"]', '1', NULL, false, NULL, NULL, NULL);

-- Insert threads
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (1001, 'main', 12345, 1, false),
    (1002, 'worker-1', 12346, 2, false),
    (1003, 'worker-2', 12347, 3, false),
    (1004, 'virtual-thread-1', NULL, 100, true);

-- Insert events with JSON fields
INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.ExecutionSample', '2025-01-15T10:00:00Z', 1000000, 1, NULL, NULL, 2001, 1001, '{"state": "RUNNABLE", "sampledThread": "main"}'),
    ('jdk.ExecutionSample', '2025-01-15T10:00:01Z', 1000000, 1, NULL, NULL, 2002, 1002, '{"state": "BLOCKED", "sampledThread": "worker-1"}'),
    ('jdk.ExecutionSample', '2025-01-15T10:00:02Z', 1000000, 1, NULL, NULL, 2003, 1001, '{"state": "RUNNABLE", "sampledThread": "main"}'),
    ('jdk.ObjectAllocationInNewTLAB', '2025-01-15T10:00:00Z', NULL, 1, 1024, NULL, 2004, 1001, '{"objectClass": "java.lang.String", "allocationSize": 1024}'),
    ('jdk.GCPhasePause', '2025-01-15T10:00:05Z', 5000000, 1, 5000000, NULL, NULL, NULL, '{"name": "G1 Young Generation", "gcId": 1}');
