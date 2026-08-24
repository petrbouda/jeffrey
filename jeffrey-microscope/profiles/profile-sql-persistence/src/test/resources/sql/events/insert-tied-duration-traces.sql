-- Fixture for JdbcTraceRepositoryTest.Reads, layered on top of insert-trace-spans.sql.
--
-- Two traces with exactly the same duration, so ordering by duration alone cannot decide between
-- them. The slowest-traces list breaks the tie on trace_id; without that, the trace at a LIMIT
-- boundary comes and goes between two identical requests.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:03.000Z', 3000, 7000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7002,"spanId":331,"parentSpanId":0,"name":"GET /tied","kind":"SERVER","status":"UNSET","method":"GET","uri":"/tied","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:04.000Z', 4000, 7000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7001,"spanId":332,"parentSpanId":0,"name":"GET /tied","kind":"SERVER","status":"UNSET","method":"GET","uri":"/tied","statusCode":200}');
