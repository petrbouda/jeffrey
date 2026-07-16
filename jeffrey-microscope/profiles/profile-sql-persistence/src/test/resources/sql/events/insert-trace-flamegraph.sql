-- Fixture for DuckDBFlamegraphJsonFieldFilterTest.
-- otel.cpu events carrying OpenTelemetry trace correlation in the JSON fields column, to prove the
-- JSON-field equality predicate keeps only the samples linked to the requested trace/span.
--
--   trace aaaa..: two samples (spans 1111.. and 2222..)
--   trace bbbb..: one sample (span 3333..)
--   one sample without any trace link (fields without trace keys)

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('otel.cpu', 'CPU (OTel)', 1, 'OpenTelemetry cpu samples', '["OpenTelemetry","CPU"]', '4', NULL, true, NULL, NULL, NULL);

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (2001, 'worker-1', 41, 12, false);

INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (9001, 'com.example.Service', 'handle', 'JIT compiled', 10, -1);

INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (5001, 100, [9001], []);

INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('otel.cpu', '2025-01-15T10:00:00.000Z', 0, NULL, 1, 10000000, NULL, 5001, 2001,
     '{"trace_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","span_id":"1111111111111111"}'),
    ('otel.cpu', '2025-01-15T10:00:00.010Z', 10, NULL, 1, 10000000, NULL, 5001, 2001,
     '{"trace_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","span_id":"2222222222222222"}'),
    ('otel.cpu', '2025-01-15T10:00:00.020Z', 20, NULL, 1, 10000000, NULL, 5001, 2001,
     '{"trace_id":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","span_id":"3333333333333333"}'),
    ('otel.cpu', '2025-01-15T10:00:00.030Z', 30, NULL, 1, 10000000, NULL, 5001, 2001,
     '{"thread.state":"RUNNABLE"}');
