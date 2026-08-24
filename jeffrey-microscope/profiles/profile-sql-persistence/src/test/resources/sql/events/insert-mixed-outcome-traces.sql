-- Fixture for JdbcTraceRepositoryTest.Filtering, layered on top of insert-trace-spans.sql.
--
-- One operation, `GET /mixed`, whose traces did not all end the same way: two succeeded and one
-- failed. That mix is what tells an errors-only filter written as a HAVING apart from one written as
-- a WHERE. A WHERE drops the two successful traces before the aggregate is taken, so the operation
-- comes back claiming one trace and a latency distribution made only of its failure; a HAVING keeps
-- the whole population and only decides whether the operation is listed at all.
--
-- The three durations are far apart so a percentile taken over the wrong subset is obvious.
INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:05.000Z', 5000, 10000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7101,"spanId":341,"parentSpanId":0,"name":"GET /mixed","kind":"SERVER","status":"OK","method":"GET","uri":"/mixed","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:06.000Z', 6000, 20000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7102,"spanId":342,"parentSpanId":0,"name":"GET /mixed","kind":"SERVER","status":"OK","method":"GET","uri":"/mixed","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:07.000Z', 7000, 90000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7103,"spanId":343,"parentSpanId":0,"name":"GET /mixed","kind":"SERVER","status":"ERROR","errorType":"java.io.IOException","method":"GET","uri":"/mixed","statusCode":500}'),
    -- An operation that never failed. Both traces in the base fixture end in an error, so without
    -- this there is nothing for an errors-only filter to leave out and the filter would pass a test
    -- even if it selected everything.
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:08.000Z', 8000, 3000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7201,"spanId":351,"parentSpanId":0,"name":"GET /clean","kind":"SERVER","status":"OK","method":"GET","uri":"/clean","statusCode":200}'),
    ('jeffrey.HttpServerExchange', '2025-01-15T10:00:09.000Z', 9000, 4000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":7202,"spanId":352,"parentSpanId":0,"name":"GET /clean","kind":"SERVER","status":"OK","method":"GET","uri":"/clean","statusCode":200}');
