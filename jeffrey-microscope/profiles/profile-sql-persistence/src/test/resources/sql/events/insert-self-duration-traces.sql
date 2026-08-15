-- Fixture for JdbcTraceRepositoryTest.SelfDuration, layered on top of insert-trace-spans.sql
-- (which registers jeffrey.TraceSpan as a span type and creates threads 3001 and 3002).
--
-- One trace per rule the self-time derivation has to obey, so a failure names the rule it broke
-- rather than "some number is wrong". Every parent runs for a round duration on thread 3001; what
-- differs is the shape of its children.
--
-- Durations are nanoseconds. The micro-precision timestamps in trace 8005 are deliberate: a span
-- shorter than a millisecond is the ordinary case for a JDBC statement, and rounding its window to
-- whole milliseconds is what used to hand its parent back time the child had spent.
INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- 8001 sequential: one child inside the parent, nothing overlapping.
    -- parent 0..100ms, child 10..40ms  ->  parent self 70ms
    ('jeffrey.TraceSpan', '2025-01-15T10:01:00.000Z', 60000, 100000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8001,"spanId":401,"parentSpanId":0,"name":"sequential-parent","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:01:00.010Z', 60010, 30000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8001,"spanId":402,"parentSpanId":401,"name":"sequential-child","kind":"INTERNAL","status":"UNSET"}'),

    -- 8002 overlapping: two children running concurrently cover 10..50ms, i.e. 40ms, not 30+30.
    -- parent 0..100ms  ->  parent self 60ms
    ('jeffrey.TraceSpan', '2025-01-15T10:02:00.000Z', 120000, 100000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8002,"spanId":411,"parentSpanId":0,"name":"overlap-parent","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:02:00.010Z', 120010, 30000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8002,"spanId":412,"parentSpanId":411,"name":"overlap-a","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:02:00.020Z', 120020, 30000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8002,"spanId":413,"parentSpanId":411,"name":"overlap-b","kind":"INTERNAL","status":"UNSET"}'),

    -- 8003 cross-thread: the child was handed to another thread, so the parent kept working the
    -- whole time and must not be charged for it.  ->  parent self 100ms
    ('jeffrey.TraceSpan', '2025-01-15T10:03:00.000Z', 180000, 100000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8003,"spanId":421,"parentSpanId":0,"name":"forking-parent","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:03:00.010Z', 180010, 30000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":8003,"spanId":422,"parentSpanId":421,"name":"forked-child","kind":"INTERNAL","status":"UNSET"}'),

    -- 8004 overrun: the child outlives its parent, so only the stretch the two shared is subtracted.
    -- parent 0..50ms, child 20..100ms -> shared 20..50ms  ->  parent self 20ms
    ('jeffrey.TraceSpan', '2025-01-15T10:04:00.000Z', 240000, 50000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8004,"spanId":431,"parentSpanId":0,"name":"overrun-parent","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:04:00.020Z', 240020, 80000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8004,"spanId":432,"parentSpanId":431,"name":"overrun-child","kind":"INTERNAL","status":"UNSET"}'),

    -- 8005 sub-millisecond: parent 0..4000us, children 500..1100us and 2000..2400us, together
    -- 1000us.  ->  parent self 3000us. Rounded to milliseconds both children would cost nothing.
    ('jeffrey.TraceSpan', '2025-01-15T10:05:00.000000Z', 300000, 4000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8005,"spanId":441,"parentSpanId":0,"name":"submilli-parent","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:05:00.000500Z', 300000, 600000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8005,"spanId":442,"parentSpanId":441,"name":"submilli-a","kind":"INTERNAL","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T10:05:00.002000Z', 300002, 400000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":8005,"spanId":443,"parentSpanId":441,"name":"submilli-b","kind":"INTERNAL","status":"UNSET"}');
