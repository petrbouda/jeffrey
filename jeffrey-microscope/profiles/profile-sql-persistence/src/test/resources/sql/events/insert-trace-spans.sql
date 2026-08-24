-- Fixture for JdbcTraceRepositoryTest.
--
-- One trace representing an HTTP request that issued two JDBC statements and ran a hand-written
-- span that failed, plus rows that must NOT become spans: an untraced JDBC statement (all ids 0)
-- and an unrelated event type. Ids are deliberately extreme -- Long.MIN_VALUE, Long.MAX_VALUE and
-- a negative span id -- so the JSON-to-BIGINT round trip is exercised at its boundaries.
--
-- Every instrumented event records the whole span shape: its own span id, a name, a kind and a
-- status. The derivation reads them verbatim, so the fixture is what an event would actually write
-- -- an HTTP exchange has already folded its method and URI into the name, and each statement
-- carries an id of its own under the exchange it ran inside.

-- `columns` is what the parser copied out of the recording's metadata, and it is how the derivation
-- recognises a span: an event type is one if it declares a `spanId` field. jdk.ExecutionSample
-- declares none, which is the whole reason it stays out of the trace tables.
INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.TraceSpan', 'Trace Span', 1, 'trace span', '["Application","Tracing"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"errorType","header":"Error Type"},{"field":"attributes","header":"Attributes"}]'),
    ('jeffrey.HttpServerExchange', 'HTTP Server Exchange', 2, 'http', '["Application","HTTP"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"method","header":"HTTP Method"},{"field":"uri","header":"HTTP Uri"},{"field":"statusCode","header":"Response Status"}]'),
    ('jeffrey.JdbcQuery', 'JDBC Query', 3, 'jdbc', '["Application","JDBC"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"sql","header":"SQL Query"},{"field":"group","header":"Label for Statement Grouping"},{"field":"rows","header":"Affected/Returned Rows"}]'),
    ('jdk.ExecutionSample', 'Execution Sample', 4, 'CPU execution sample', '["Profiling"]', '1', NULL, true, NULL, NULL,
     '[{"field":"sampledThread","header":"Thread"},{"field":"state","header":"Thread State"}]');

-- The request thread is virtual, as it is in any application serving requests on virtual threads,
-- and the forked work lands on a platform pool thread. The difference decides whether a span can be
-- matched to samples at all: the profiler attributes those to the carrier.
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (3001, 'tomcat-handler-1', NULL, 21, true),
    (3002, 'pool-1-thread-1', 52, 22, false);

-- Durations in nanoseconds. Trace 9223372036854775807 is Long.MAX_VALUE; the hand-written span
-- carries a negative span id, which must survive the round trip with its sign.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.000Z',   0, 120000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":111,"parentSpanId":0,"name":"POST /api/internal/profiles/{profileId}/flamegraph","kind":"SERVER","status":"UNSET","method":"POST","uri":"/api/internal/profiles/{profileId}/flamegraph","statusCode":200}'),
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:00.010Z',  10,  40000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":112,"parentSpanId":111,"name":"listSpans","kind":"CLIENT","status":"UNSET","group":"PROFILE_EVENTS","sql":"SELECT * FROM spans","rows":42}'),
    -- A second statement issued inside the very same span: each carries an id of its own, so the two
    -- are two spans rather than one row the waterfall cannot tell apart.
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:00.020Z',  20,   5000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":113,"parentSpanId":111,"name":"countSpans","kind":"CLIENT","status":"UNSET","group":"PROFILE_EVENTS"}'),
    -- Started 750 microseconds into its millisecond, so a projection that floors the start to whole
    -- milliseconds is visible rather than indistinguishable from one that does not.
    ('jeffrey.TraceSpan',          '2025-01-15T10:00:00.060750Z',  60,  20000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":9223372036854775807,"spanId":-8113938001533374712,"parentSpanId":111,"name":"flamegraph.generate","kind":"INTERNAL","status":"ERROR","errorType":"java.lang.IllegalStateException","attributes":"{\"eventType\":\"jdk.ExecutionSample\",\"depth\":7}"}'),
    -- A second, faster trace, so ordering by duration has something to order. The exchange failed,
    -- and says so itself rather than leaving a reader to work it out from the status code.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:01.000Z', 1000,  5000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":-9223372036854775808,"spanId":222,"parentSpanId":0,"name":"GET /api/internal/health","kind":"SERVER","status":"ERROR","method":"GET","uri":"/api/internal/health","statusCode":500}'),
    -- Untraced: every id is 0, the wire encoding for "absent".
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:02.000Z', 2000,  1000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":0,"spanId":0,"parentSpanId":0,"name":"untraced","kind":"CLIENT","status":"UNSET"}'),
    -- Not a span-shaped event type at all: it declares no spanId, so nothing here can lift it.
    ('jdk.ExecutionSample',        '2025-01-15T10:00:00.030Z',   30,      NULL, 1, NULL, NULL, NULL, 3001, '{}');
