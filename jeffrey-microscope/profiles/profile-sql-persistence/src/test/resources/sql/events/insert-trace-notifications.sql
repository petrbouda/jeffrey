-- Fixture for JdbcTraceRepositoryTest.NotificationAndExceptionDerivation, layered on
-- insert-trace-spans.sql (which registers the span types, creates threads 3001 and 3002, and gives
-- trace Long.MAX_VALUE its spans).
--
-- The span windows this leans on, all in trace 9223372036854775807:
--   111  thread 3001  .000 .. .120   root HTTP exchange
--   112  thread 3001  .010 .. .050   JDBC query, inside 111
--   113  thread 3001  .020 .. .025   JDBC query, inside 111 and overlapping 112
--   -8113938001533374712  thread 3002  .060750 .. .080750  ERROR, java.lang.IllegalStateException
--
-- Notifications carry their own ids, so every case here is about what those ids resolve to.
-- Exceptions carry none, so every case there is about which window wins.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    -- Note what is NOT here: a field called spanId. Span discovery is structural, so a notification
    -- naming its field that way would be built into a nameless, durationless span under everything
    -- that ever said anything. It declares enclosingSpanId instead, and one of the tests proves the
    -- discovery leaves it alone.
    ('jeffrey.Notification', 'Notification', 40, 'application notification', '["Application","Notification"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"enclosingSpanId","header":"Enclosing Span Id"},{"field":"type","header":"Type"},{"field":"title","header":"Title"},{"field":"message","header":"Message"},{"field":"severity","header":"Severity"},{"field":"category","header":"Category"},{"field":"source","header":"Source"}]'),
    ('jdk.JavaExceptionThrow', 'Java Exception Throw', 41, 'exception thrown', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"message","header":"Message"},{"field":"thrownClass","header":"Class"}]'),
    ('jdk.JavaErrorThrow', 'Java Error Throw', 42, 'error thrown', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"message","header":"Message"},{"field":"thrownClass","header":"Class"}]');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- ---------------------------------------------------------------- notifications
    -- Raised inside span 112 and saying so. The ordinary case.
    ('jeffrey.Notification', '2025-01-15T10:00:00.012Z', 12, NULL, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"enclosingSpanId":112,"type":"QUERY_PLAN_FALLBACK","title":"Query fell back to a sequential scan","message":"orders_customer_idx was not usable for this predicate","severity":"MEDIUM","category":"PERFORMANCE","source":"orders-repo"}'),

    -- Two at the very same instant on the very same span. They are two things, and the derivation
    -- has to keep them apart without a natural key to do it with.
    ('jeffrey.Notification', '2025-01-15T10:00:00.030Z', 30, NULL, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"enclosingSpanId":111,"type":"CACHE_WARMED","title":"Cache warmed","message":"first","severity":"LOW","category":"PERFORMANCE","source":"cache"}'),
    ('jeffrey.Notification', '2025-01-15T10:00:00.030Z', 30, NULL, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"enclosingSpanId":111,"type":"CACHE_WARMED","title":"Cache warmed","message":"second","severity":"LOW","category":"PERFORMANCE","source":"cache"}'),

    -- In the trace, but no span was open: it belongs to the trace and to no bar.
    ('jeffrey.Notification', '2025-01-15T10:00:00.100Z', 100, NULL, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"enclosingSpanId":0,"type":"FEATURE_FLAG_READ","title":"Flag evaluated","message":"async-persist off","severity":"LOW","category":"CONFIGURATION","source":"flags"}'),

    -- Names a span this profile does not hold -- below a threshold, or from a chunk never ingested.
    -- Must read exactly like the one above: in the trace, nothing to draw it on.
    ('jeffrey.Notification', '2025-01-15T10:00:00.105Z', 105, NULL, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"enclosingSpanId":987654321,"type":"POOL_PRESSURE","title":"Pool at 92% of max","message":"46 of 50 handed out","severity":"MEDIUM","category":"RESOURCE","source":"hikari"}'),

    -- No trace was open. Belongs to no trace, so no trace should ever show it.
    ('jeffrey.Notification', '2025-01-15T10:00:00.110Z', 110, NULL, 1, NULL, NULL, NULL, 3001,
     '{"traceId":0,"enclosingSpanId":0,"type":"STARTUP_COMPLETE","title":"Startup finished","message":"untraced","severity":"LOW","category":"LIFECYCLE","source":"boot"}'),

    -- Carries a trace id no trace in this profile has.
    ('jeffrey.Notification', '2025-01-15T10:00:00.111Z', 111, NULL, 1, NULL, NULL, NULL, 3001,
     '{"traceId":424242,"enclosingSpanId":0,"type":"ORPHANED","title":"Orphaned","message":"no such trace","severity":"HIGH","category":"SYSTEM","source":"ghost"}'),

    -- ---------------------------------------------------------------- exceptions
    -- Inside 111 and 112. The innermost is 112, and the 120ms root must not claim it.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:00.015Z', 15, NULL, 1, NULL, NULL, 7001, 3001,
     '{"thrownClass":"java.lang.NumberFormatException","message":"For input string: \"1,299.00\""}'),

    -- Inside 111, 112 and 113. The narrowest window of the three is 113, at five milliseconds.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:00.022Z', 22, NULL, 1, NULL, NULL, 7002, 3001,
     '{"thrownClass":"java.io.IOException","message":"stream closed"}'),

    -- Thrown inside the span that failed, with the class that span reports as its error type: this
    -- is the throw that escaped, and the one that gives a bare class name a stack.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:00.070Z', 70, NULL, 1, NULL, NULL, 7003, 3002,
     '{"thrownClass":"java.lang.IllegalStateException","message":"no flamegraph for jdk.ExecutionSample"}'),

    -- Same span, different class: caught, and must not be marked escaped just because its span failed.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:00.071Z', 71, NULL, 1, NULL, NULL, NULL, 3002,
     '{"thrownClass":"java.util.NoSuchElementException","message":"empty"}'),

    -- An Error rather than an Exception, so the event type has to survive to tell them apart.
    ('jdk.JavaErrorThrow', '2025-01-15T10:00:00.072Z', 72, NULL, 1, NULL, NULL, NULL, 3002,
     '{"thrownClass":"java.lang.StackOverflowError","message":null}'),

    -- After every span of the trace ended: no window contains it, so it is not in any trace.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:00.500Z', 500, NULL, 1, NULL, NULL, NULL, 3001,
     '{"thrownClass":"java.lang.RuntimeException","message":"after the trace"}'),

    -- On a thread that ran no span at all.
    ('jdk.JavaExceptionThrow', '2025-01-15T10:00:00.015Z', 15, NULL, 1, NULL, NULL, NULL, 9999,
     '{"thrownClass":"java.lang.RuntimeException","message":"unrelated thread"}');
