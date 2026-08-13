-- Fixture for JdbcTraceRepositoryTest.
--
-- One trace representing an HTTP request that issued two JDBC statements and ran a hand-written
-- span that failed, plus rows that must NOT become spans: an untraced JDBC statement (all ids 0)
-- and an unrelated event type. Ids are deliberately extreme -- Long.MIN_VALUE, Long.MAX_VALUE and
-- a negative span id -- so the JSON-to-BIGINT round trip is exercised at its boundaries.
--
-- The two event shapes are recorded differently, and the fixture keeps them apart. An exchange or
-- a hand-written span opens a span of its own (Tracer.inSpanOf), so its spanId is its own. A JDBC
-- statement is only stamped with whatever span is in progress (Tracer.stamp), so its spanId is the
-- *enclosing* span's -- which is why both statements below carry span 111, the HTTP exchange's.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.TraceSpan', 'Trace Span', 1, 'trace span', '["Application","Tracing"]', '1', NULL, false, NULL, NULL, NULL),
    ('jeffrey.HttpServerExchange', 'HTTP Server Exchange', 2, 'http', '["Application","HTTP"]', '1', NULL, false, NULL, NULL, NULL),
    ('jeffrey.JdbcQuery', 'JDBC Query', 3, 'jdbc', '["Application","JDBC"]', '1', NULL, false, NULL, NULL, NULL),
    ('jdk.ExecutionSample', 'Execution Sample', 4, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL, NULL);

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (3001, 'http-nio-exec-1', 51, 21, false),
    (3002, 'pool-1-thread-1', 52, 22, false);

-- Durations in nanoseconds. Trace 9223372036854775807 is Long.MAX_VALUE; the hand-written span
-- carries a negative span id, which must survive the round trip with its sign.
INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.000Z',   0, 120000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":111,"parentSpanId":0,"method":"POST","uri":"/api/internal/profiles/{profileId}/flamegraph","status":200}'),
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:00.010Z',  10,  40000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":111,"parentSpanId":0,"name":"listSpans","group":"PROFILE_EVENTS","isSuccess":true}'),
    -- A second statement stamped with the very same span: the two must still become two spans.
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:00.020Z',  20,   5000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":111,"parentSpanId":0,"name":"countSpans","group":"PROFILE_EVENTS","isSuccess":true}'),
    ('jeffrey.TraceSpan',          '2025-01-15T10:00:00.060Z',  60,  20000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":9223372036854775807,"spanId":-8113938001533374712,"parentSpanId":111,"name":"flamegraph.generate","kind":"INTERNAL","status":"ERROR","errorType":"java.lang.IllegalStateException"}'),
    -- A second, faster trace, so ordering by duration has something to order.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:01.000Z', 1000,  5000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":-9223372036854775808,"spanId":222,"parentSpanId":0,"method":"GET","uri":"/api/internal/health","status":500}'),
    -- Untraced: every id is 0, the wire encoding for "absent".
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:02.000Z', 2000,  1000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":0,"spanId":0,"parentSpanId":0,"name":"untraced"}'),
    -- Not a traced event type at all.
    ('jdk.ExecutionSample',        '2025-01-15T10:00:00.030Z',   30,      NULL, 1, NULL, NULL, NULL, 3001, '{}');
