-- Exchanges of both shapes, side by side, for the cases where the recorded field and the convention
-- can disagree.
--
-- One `columns` list per event type covers both shapes -- it is the union of what either records --
-- because the derivation reads keys out of the JSON and only consults metadata to decide that a
-- type is a span at all.
--
-- Loaded on its own. Six traces:
--   601  a self-describing GET /api/internal/health
--   602  the same endpoint recorded without a span shape        -> must be the same operation as 601
--   603  an exchange whose recorded name disagrees with its own method and URI
--   604  an exchange that threw and still answered 200
--   605  399, the last response code that is not a failure
--   606  400, the first that is

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.HttpServerExchange', 'HTTP Server Exchange', 1, 'http', '["Application","HTTP"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"errorType","header":"Error Type"},{"field":"method","header":"HTTP Method"},{"field":"uri","header":"HTTP Uri"},{"field":"statusCode","header":"Response Status"}]');

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (5001, 'tomcat-handler-1', 81, 41, false);

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The same endpoint, recorded by two versions of the instrumentation.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.000Z',   0, 10000000, 1, NULL, NULL, NULL, 5001,
     '{"traceId":601,"spanId":6011,"parentSpanId":0,"name":"GET /api/internal/health","kind":"SERVER","status":"UNSET","method":"GET","uri":"/api/internal/health","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.100Z', 100, 12000000, 1, NULL, NULL, NULL, 5001,
     '{"traceId":602,"spanId":6021,"parentSpanId":0,"method":"GET","uri":"/api/internal/health","status":200}'),

    -- A name that is not the one the convention gives this method and URI.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.200Z', 200, 14000000, 1, NULL, NULL, NULL, 5001,
     '{"traceId":603,"spanId":6031,"parentSpanId":0,"name":"legacy-handler-42","kind":"SERVER","status":"UNSET","method":"POST","uri":"/orders","statusCode":201}'),

    -- Threw, and still answered 200: the one outcome no response code can express.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.300Z', 300, 16000000, 1, NULL, NULL, NULL, 5001,
     '{"traceId":604,"spanId":6041,"parentSpanId":0,"name":"GET /api/internal/ping","kind":"SERVER","status":"ERROR","errorType":"java.lang.IllegalStateException","method":"GET","uri":"/api/internal/ping","statusCode":200}'),

    -- The two sides of the 400 boundary, which is spelled once in the event and once in the SQL.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.400Z', 400,  4000000, 1, NULL, NULL, NULL, 5001,
     '{"traceId":605,"spanId":6051,"parentSpanId":0,"name":"GET /api/internal/redirect","kind":"SERVER","status":"UNSET","method":"GET","uri":"/api/internal/redirect","statusCode":399}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:00.500Z', 500,  4000000, 1, NULL, NULL, NULL, 5001,
     '{"traceId":606,"spanId":6061,"parentSpanId":0,"name":"GET /api/internal/bad-request","kind":"SERVER","status":"UNSET","method":"GET","uri":"/api/internal/bad-request","statusCode":400}');
