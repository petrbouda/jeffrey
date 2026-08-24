-- Fixture for JdbcTraceRepositoryTest.Context, layered on top of insert-trace-spans.sql
-- (which registers jeffrey.TraceSpan as a span type and creates threads 3001 and 3002).
--
-- The scenario the whole feature exists for: a span that looks slow because of its own code, and
-- is not. Trace 9001 runs one span for 300ms on thread 3001; inside it a 100ms collection pause
-- stops the world and a 30ms monitor wait blocks the thread. Two thirds of that span is the JVM.
--
-- Thread 3003 is a VM thread. It matters that the pauses are recorded on it and not on 3001: that
-- is why they cannot be found by matching the span's own thread, and why a global window query has
-- to exist at all. A test that put them on 3001 would pass against a thread-scoped query and prove
-- nothing.
INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES (3003, 'VM Thread', 91, NULL, false);

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.GCPhasePause', 'GC Phase Pause', 20, 'gc pause', '["Java Virtual Machine","GC"]', '1', NULL, false, NULL, NULL,
     '[{"field":"name","header":"Name"},{"field":"gcId","header":"GC Identifier"}]'),
    ('jdk.ExecuteVMOperation', 'VM Operation', 21, 'vm op', '["Java Virtual Machine","Runtime"]', '1', NULL, false, NULL, NULL,
     '[{"field":"operation","header":"Operation"},{"field":"safepoint","header":"At Safepoint"}]'),
    ('jdk.JavaMonitorEnter', 'Java Monitor Blocked', 22, 'monitor', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"monitorClass","header":"Monitor Class"},{"field":"previousOwner","header":"Previous Owner"}]'),
    ('jdk.ThreadPark', 'Thread Park', 23, 'park', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"parkedClass","header":"Class Parked On"}]'),
    ('jdk.GCPhasePauseLevel1', 'GC Phase Pause Level 1', 24, 'gc pause phase', '["Java Virtual Machine","GC"]', '1', NULL, false, NULL, NULL,
     '[{"field":"name","header":"Name"},{"field":"gcId","header":"GC Identifier"}]');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The span under test: 10:10:00.000 .. 10:10:00.300 on thread 3001.
    ('jeffrey.TraceSpan', '2025-01-15T10:10:00.000Z', 600000, 300000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9001,"spanId":501,"parentSpanId":0,"name":"reserveInventory","kind":"SERVER","status":"UNSET"}'),

    -- A 100ms collection pause inside it, on the VM thread.
    ('jdk.GCPhasePause', '2025-01-15T10:10:00.100Z', 600100, 100000000, 1, NULL, NULL, NULL, 3003,
     '{"name":"G1 Young","gcId":42}'),

    -- A safepoint after it ends, so the window filter has something to exclude.
    ('jdk.ExecuteVMOperation', '2025-01-15T10:10:00.500Z', 600500, 5000000, 1, NULL, NULL, NULL, 3003,
     '{"operation":"RevokeBias","safepoint":true}'),

    -- A 40ms pause that started *before* the span and overlaps its first 10ms. Starts-inside
    -- semantics miss this one entirely, which is the bug the overlap predicate exists to prevent.
    ('jdk.GCPhasePause', '2025-01-15T10:09:59.970Z', 599970, 40000000, 1, NULL, NULL, NULL, 3003,
     '{"name":"G1 Young","gcId":41}'),

    -- Two phases of that same collection, recorded from inside it. Both are real events and both
    -- are already covered by the 100ms band above, which is what makes them detail rather than
    -- pauses of their own.
    --
    -- The first lasts 41ns. Rounded into microseconds it is a pause of no length at all, and the
    -- waterfall draws a pause of no length at the band floor -- wider, on a four-second trace, than
    -- a real 15ms collection.
    ('jdk.GCPhasePauseLevel1', '2025-01-15T10:10:00.120Z', 600120, 41, 1, NULL, NULL, NULL, 3003,
     '{"name":"Ext Root Scanning","gcId":42}'),

    -- The second has no duration at all: JFR writes this phase with a null one. It is not an
    -- interval and must not be drawn as one.
    ('jdk.GCPhasePauseLevel1', '2025-01-15T10:10:00.150Z', 600150, NULL, 1, NULL, NULL, NULL, 3003,
     '{"name":"Notify and keep alive finalizable","gcId":42}'),

    -- Thread-scoped waiting inside the span: 30ms blocked on a monitor, 20ms parked.
    ('jdk.JavaMonitorEnter', '2025-01-15T10:10:00.210Z', 600210, 30000000, 1, NULL, NULL, NULL, 3001,
     '{"monitorClass":"java.lang.Object","previousOwner":"worker-2"}'),
    ('jdk.ThreadPark', '2025-01-15T10:10:00.250Z', 600250, 20000000, 1, NULL, NULL, NULL, 3001,
     '{"parkedClass":"java.util.concurrent.locks.ReentrantLock"}'),

    -- Waiting on a thread the span never ran on, and outside its window: neither may be attributed.
    ('jdk.JavaMonitorEnter', '2025-01-15T10:10:00.210Z', 600210, 90000000, 1, NULL, NULL, NULL, 3002,
     '{"monitorClass":"java.lang.String","previousOwner":"other"}'),
    ('jdk.ThreadPark', '2025-01-15T10:10:00.900Z', 600900, 10000000, 1, NULL, NULL, NULL, 3001,
     '{"parkedClass":"after.the.span"}');
