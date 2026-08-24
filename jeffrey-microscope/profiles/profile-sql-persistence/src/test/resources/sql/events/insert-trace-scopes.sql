-- Fixture for JdbcTraceRepositoryTest.OperationIntervals, layered on top of insert-trace-spans.sql.
--
-- The HTTP exchange of the slow trace was re-entered on a thread of its own -- what a gRPC call or
-- any other callback-driven protocol does, where the work arrives in pieces after the interceptor
-- that opened the span has returned. The span's own event can only name the thread that ended it,
-- so without the scope events the third thread is invisible and its samples are never offered.

-- A scope declares `scopedSpanId`, deliberately not `spanId`: the derivation recognises spans by a
-- declared `spanId` field, and a scope is not a span -- it has no name, kind or outcome, and would
-- otherwise be drawn in the waterfall as a duplicate of the span it merely locates.
INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.TraceScope', 'Trace Scope', 5, 'trace scope', '["Application","Tracing"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"scopedSpanId","header":"Scoped Span Id"}]');

-- The thread the callback landed on. Platform, like any transport thread.
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES (3003, 'grpc-default-executor-0', 53, 23, false);

-- Two activations of span 111. The first is on the thread the span already reports, so it must not
-- widen that interval; the second is the one that would otherwise be lost.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.TraceScope', '2025-01-15T10:00:00.005Z',  5, 10000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"scopedSpanId":111}'),
    ('jeffrey.TraceScope', '2025-01-15T10:00:00.090Z', 90, 20000000, 1, NULL, NULL, NULL, 3003,
     '{"traceId":9223372036854775807,"scopedSpanId":111}');
