-- Fixture for JdbcProfileEventRepositoryTest.DurationStatsByTypeMethod
-- Inserts jdk.SafepointBegin events with varying durations (including nulls)
-- plus a few jdk.ExecutionSample events to confirm the query filters by type.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.SafepointBegin', 'Safepoint Begin', 1, 'Safepoint begin event', '["JVM"]', '1', NULL, false, NULL, NULL, NULL),
    ('jdk.ExecutionSample', 'Execution Sample', 2, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL);

-- Durations in nanoseconds. Deliberately asymmetric so p99 != max and != mean.
-- Target data set: 10 non-null safepoint durations (1ms, 2ms, 3ms, 4ms, 5ms, 6ms, 7ms, 8ms, 9ms, 500ms)
-- plus 2 null-duration safepoint rows (must NOT be counted). Sum = (1+2+...+9+500) ms = 545 ms.
-- Max = 500 ms. With 10 values, p99 via quantile_cont(0.99) interpolates between indices 8 and 9
-- → 9 ms + 0.9 * (500 - 9) ms ≈ 450.9 ms.
INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.SafepointBegin', '2025-01-15T10:00:00Z',   1000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:01Z',   2000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:02Z',   3000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:03Z',   4000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:04Z',   5000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:05Z',   6000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:06Z',   7000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:07Z',   8000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:08Z',   9000000, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:09Z', 500000000, 1, NULL, NULL, NULL, NULL, '{}'),
    -- NULL-duration safepoints — must be excluded from count/sum/p99
    ('jdk.SafepointBegin', '2025-01-15T10:00:10Z',      NULL, 1, NULL, NULL, NULL, NULL, '{}'),
    ('jdk.SafepointBegin', '2025-01-15T10:00:11Z',      NULL, 1, NULL, NULL, NULL, NULL, '{}'),
    -- Different event type — must NOT be picked up by a query filtered to jdk.SafepointBegin
    ('jdk.ExecutionSample', '2025-01-15T10:00:12Z', 999999999, 1, NULL, NULL, NULL, NULL, '{}');
