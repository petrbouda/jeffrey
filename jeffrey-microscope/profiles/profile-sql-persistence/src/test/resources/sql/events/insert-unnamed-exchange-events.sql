-- A recording whose events carry the operation's own fields and no span shape.
--
-- Two things produce it: instrumentation older than the shape `AbstractTracedEvent` now declares,
-- and third-party instrumentation that stamps the trace ids and its own domain fields without
-- describing a span. Either way an exchange recorded its method, its URI and an integer `status`,
-- and left the naming to whoever reads it.
--
-- There is nothing here for a `name` or a `kind` projection to read back, so this fixture is what
-- proves the conventions are doing the naming: an HTTP exchange is `{method} {route}`, a gRPC call
-- is `{service}/{method}`, and a statement is a client call whose outcome is its `isSuccess` flag.
--
-- Loaded on its own, not on top of insert-trace-spans.sql: the two describe the same event types
-- with different schemas, which is the point of the fixture.

-- `columns` is what the parser copied out of the recording's metadata, and it is how the derivation
-- recognises a span: an event type is one if it declares a `spanId` field. That much these types
-- always declared -- it is the span shape around it that is missing.
INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.HttpServerExchange', 'HTTP Server Exchange', 1, 'http', '["Application","HTTP"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"method","header":"HTTP Method"},{"field":"uri","header":"HTTP Uri"},{"field":"status","header":"Response Status"}]'),
    ('jeffrey.GrpcServerExchange', 'gRPC Server Exchange', 2, 'grpc', '["Application","gRPC"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"service","header":"Service Name"},{"field":"method","header":"Method Name"},{"field":"status","header":"Status Code"}]'),
    ('jeffrey.JdbcQuery', 'JDBC Query', 3, 'jdbc', '["Application","JDBC"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Statement Name"},{"field":"sql","header":"SQL Query"},{"field":"isSuccess","header":"Successful Execution"}]');

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (4001, 'tomcat-handler-1', 71, 31, false);

-- Two requests of the same endpoint, one of which answered 500, and one gRPC call that failed. The
-- statements carry a name of their own -- that field a statement always had -- so what is missing
-- from them is the kind and the outcome, not the name.
INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.000Z',   0, 30000000, 1, NULL, NULL, NULL, 4001,
     '{"traceId":501,"spanId":5011,"parentSpanId":0,"method":"GET","uri":"/api/internal/health","status":200}'),
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:00.010Z',  10,  5000000, 1, NULL, NULL, NULL, 4001,
     '{"traceId":501,"spanId":5012,"parentSpanId":5011,"name":"listSpans","sql":"SELECT * FROM spans","isSuccess":true}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.100Z', 100, 40000000, 1, NULL, NULL, NULL, 4001,
     '{"traceId":502,"spanId":5021,"parentSpanId":0,"method":"GET","uri":"/api/internal/health","status":500}'),
    ('jeffrey.GrpcServerExchange', '2025-01-15T10:00:00.200Z', 200, 20000000, 1, NULL, NULL, NULL, 4001,
     '{"traceId":503,"spanId":5031,"parentSpanId":0,"service":"jeffrey.api.v1.ProjectService","method":"List","status":"UNAVAILABLE"}'),
    ('jeffrey.JdbcQuery',          '2025-01-15T10:00:00.210Z', 210,  5000000, 1, NULL, NULL, NULL, 4001,
     '{"traceId":503,"spanId":5032,"parentSpanId":5031,"name":"countProjects","sql":"SELECT count(*) FROM projects","isSuccess":false}');
