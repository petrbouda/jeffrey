-- Fixture for JdbcTraceRepositoryTest.Derivation, layered on top of insert-trace-spans.sql.
--
-- A second event claiming span 112 of the slow trace -- a re-imported chunk, or third-party
-- instrumentation that reused an id. The derivation must keep exactly one row per (trace, span):
-- the earliest occurrence, which is the row the waterfall would have drawn anyway, so span_count
-- agrees with what the UI renders.
INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.JdbcQuery', '2025-01-15T10:00:00.015Z', 15, 99000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":112,"parentSpanId":111,"name":"listSpans-duplicate","kind":"CLIENT","status":"UNSET","group":"PROFILE_EVENTS"}');
