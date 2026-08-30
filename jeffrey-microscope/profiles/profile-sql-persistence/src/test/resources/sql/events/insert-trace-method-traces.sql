-- Fixture for JdbcTraceRepositoryTest.MethodPromotion, layered on insert-trace-spans.sql
-- (which registers jeffrey.TraceSpan as a span type and creates threads 3001 and 3002).
--
-- The shape is taken from a real JDK 25 recording rather than invented. A probe with two traced
-- methods, the outer calling the inner, and a socket read inside the inner one, produced exactly
-- this nesting:
--
--   outer      [533, 747]   method = Probe.outer()   stackTrace leaf = Probe.main
--     inner    [553, 727]   method = Probe.inner()   stackTrace leaf = Probe.outer   <- the CALLER
--       read   [603, 724]
--
-- Two things that fixture proves and this one reproduces. First, a jdk.MethodTrace duration
-- INCLUDES the methods it calls, so traced methods nest as intervals -- which is the whole reason
-- a method span has to be able to parent another span. Second, JEP 520 roots the stack trace at the
-- CALLER, so weight_entity (the leaf frame) names the wrong method and the name has to come from
-- the event's own `method` field.
--
-- Timings below are the probe's, shifted onto the 10:10:00 span the other trace fixtures use.
INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.MethodTrace', 'Method Trace', 40, 'method tracing', '["Java Virtual Machine","Method Tracing"]', '1', NULL, true, NULL, NULL,
     '[{"field":"method","header":"Method"}]'),
    ('jdk.SocketRead', 'Socket Read', 41, 'socket read', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"host","header":"Remote Host"},{"field":"bytesRead","header":"Bytes Read"}]');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The recorded span everything hangs under: 10:10:00.000 .. 10:10:00.300 on thread 3001.
    ('jeffrey.TraceSpan', '2025-01-15T10:10:00.000Z', 600000, 300000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9003,"spanId":701,"parentSpanId":0,"name":"reserveInventory","kind":"SERVER","status":"UNSET"}'),

    -- outer: 10:10:00.020 .. 10:10:00.234 (214ms). weight_entity is the CALLER, as JFR records it.
    ('jdk.MethodTrace', '2025-01-15T10:10:00.020Z', 600020, 214000000, 1, 214000000, 'Probe#main', NULL, 3001,
     '{"method":"cafe.jeffrey.probe.Probe#outer"}'),

    -- inner: 10:10:00.040 .. 10:10:00.214 (174ms), nested inside outer. Its leaf frame is outer,
    -- so a name taken from weight_entity would call this span "outer" too.
    ('jdk.MethodTrace', '2025-01-15T10:10:00.040Z', 600040, 174000000, 1, 174000000, 'Probe#outer', NULL, 3001,
     '{"method":"cafe.jeffrey.probe.Probe#inner"}'),

    -- The socket read inside inner: 10:10:00.090 .. 10:10:00.211 (121ms). Its parent must be the
    -- inner method, not the recorded span -- which is what promotion into a non-leaf tests.
    ('jdk.SocketRead', '2025-01-15T10:10:00.090Z', 600090, 121000000, 1, NULL, NULL, NULL, 3001,
     '{"host":"127.0.0.1","bytesRead":4}'),

    -- A traced method on a thread with no recorded span open: it belongs to no trace and must not
    -- be promoted at all.
    ('jdk.MethodTrace', '2025-01-15T10:10:00.100Z', 600100, 5000000, 1, 5000000, 'Other#caller', NULL, 3002,
     '{"method":"cafe.jeffrey.probe.Other#orphan"}'),

    -- A traced method after the recorded span closed: in range of nothing, promoted nowhere.
    ('jdk.MethodTrace', '2025-01-15T10:10:00.900Z', 600900, 5000000, 1, 5000000, 'Probe#main', NULL, 3001,
     '{"method":"cafe.jeffrey.probe.Probe#afterTheSpan"}'),

    -- Two traced methods beginning on the SAME microsecond with the SAME duration. Containment
    -- alone lets each adopt the other and the tree closes into a cycle; only the strict ordering
    -- guard keeps this a tree. Which of the two wins does not matter, but it must be one of them
    -- and it must be stable.
    ('jdk.MethodTrace', '2025-01-15T10:10:00.250Z', 600250, 10000000, 1, 10000000, 'Probe#main', NULL, 3001,
     '{"method":"cafe.jeffrey.probe.Probe#twinA"}'),
    ('jdk.MethodTrace', '2025-01-15T10:10:00.250Z', 600250, 10000000, 1, 10000000, 'Probe#main', NULL, 3001,
     '{"method":"cafe.jeffrey.probe.Probe#twinB"}');
