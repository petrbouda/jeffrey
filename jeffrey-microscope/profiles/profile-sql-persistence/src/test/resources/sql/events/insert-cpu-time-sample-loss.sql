-- Fixture for JdbcProfileEventRepositoryTest.CpuTimeSampleLossMethod
-- Models what the JDK CPU-time sampler writes: the samples that survived, plus the
-- jdk.CPUTimeSamplesLost events that account for the ones the queue dropped. One loss event
-- normally stands for SEVERAL lost samples, which is the whole point of the lostSamples field.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.CPUTimeSample', 'CPU Time Sample', 1, 'CPU time sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL),
    ('jdk.CPUTimeSamplesLost', 'CPU Time Samples Lost', 2, 'Lost CPU time samples', '["Profiling"]', '1', NULL, false, NULL, NULL, NULL),
    ('jdk.ExecutionSample', 'Execution Sample', 3, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL);

-- 4 captured samples, 3 loss events carrying 2 + 5 + 1 = 8 lost samples.
INSERT INTO events (event_type, start_timestamp, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jdk.CPUTimeSample', '2025-01-15T10:00:00Z', NULL, 1, 10000000, NULL, NULL, NULL, '{"samplingPeriod":10000000,"failed":false,"biased":false}'),
    ('jdk.CPUTimeSample', '2025-01-15T10:00:01Z', NULL, 1, 10000000, NULL, NULL, NULL, '{"samplingPeriod":10000000,"failed":false,"biased":false}'),
    ('jdk.CPUTimeSample', '2025-01-15T10:00:02Z', NULL, 1, 10000000, NULL, NULL, NULL, '{"samplingPeriod":10000000,"failed":false,"biased":true}'),
    ('jdk.CPUTimeSample', '2025-01-15T10:00:03Z', NULL, 1, 10000000, NULL, NULL, NULL, '{"samplingPeriod":10000000,"failed":false,"biased":true}'),
    ('jdk.CPUTimeSamplesLost', '2025-01-15T10:00:01Z', NULL, 1, NULL, NULL, NULL, NULL, '{"lostSamples":2}'),
    ('jdk.CPUTimeSamplesLost', '2025-01-15T10:00:02Z', NULL, 1, NULL, NULL, NULL, NULL, '{"lostSamples":5}'),
    ('jdk.CPUTimeSamplesLost', '2025-01-15T10:00:03Z', NULL, 1, NULL, NULL, NULL, NULL, '{"lostSamples":1}'),
    -- A different sampler's events — must not be counted as captured CPU-time samples.
    ('jdk.ExecutionSample', '2025-01-15T10:00:04Z', NULL, 1, NULL, NULL, NULL, NULL, '{"state":"RUNNABLE"}'),
    ('jdk.ExecutionSample', '2025-01-15T10:00:05Z', NULL, 1, NULL, NULL, NULL, NULL, '{"state":"RUNNABLE"}');
