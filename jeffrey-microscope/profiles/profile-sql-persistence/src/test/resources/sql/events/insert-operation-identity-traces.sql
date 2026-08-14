-- Applied on top of insert-trace-spans.sql, for the two cases an operation keyed by name alone
-- gets wrong.
--
-- 1. An outbound `GET /api/internal/health` alongside the inbound one already in the base fixture.
--    Same name, same convention, different instrumentation -- two operations, not one.
-- 2. A `POST /orders` that calls itself, so the trace holds a nested span named exactly like its
--    root. Excluding the root from the breakdown by name dropped the recursion with it.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.HttpClientExchange', 'HTTP Client Exchange', 5, 'http client', '["Application","HTTP"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"method","header":"HTTP Method"},{"field":"uri","header":"HTTP Uri"},{"field":"statusCode","header":"Response Status"}]');

INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The outbound call. Named identically to the inbound exchange in the base fixture.
    ('jeffrey.HttpClientExchange', '2025-01-15T10:00:03.000Z', 3000, 7000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":1001,"spanId":501,"parentSpanId":0,"name":"GET /api/internal/health","kind":"CLIENT","status":"UNSET","method":"GET","uri":"/api/internal/health","statusCode":200}'),

    -- A handler that re-enters itself: root, then a nested span of the same name, then a leaf.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:04.000Z', 4000, 90000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":1002,"spanId":601,"parentSpanId":0,"name":"POST /orders","kind":"SERVER","status":"UNSET","method":"POST","uri":"/orders","statusCode":200}'),
    ('jeffrey.TraceSpan',          '2025-01-15T10:00:04.010Z', 4010, 50000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":1002,"spanId":602,"parentSpanId":601,"name":"POST /orders","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan',          '2025-01-15T10:00:04.020Z', 4020, 10000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":1002,"spanId":603,"parentSpanId":602,"name":"orders.persist","kind":"INTERNAL","status":"UNSET"}');
