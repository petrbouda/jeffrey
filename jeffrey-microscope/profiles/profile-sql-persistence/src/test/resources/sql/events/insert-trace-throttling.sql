-- Fixture for JdbcTraceRepositoryTest.Throttling, layered on top of insert-trace-spans.sql
-- (which registers jeffrey.TraceSpan as a span type and creates threads 3001 and 3002).
--
-- jdk.ContainerCPUThrottling is nothing like the other context events. It is a periodic sample of
-- three cgroup counters that are CUMULATIVE SINCE THE CGROUP WAS CREATED, so no row here describes
-- a stretch of throttling -- every window is inferred from the pair of samples that bound it. That
-- is what these samples exist to exercise: the absolute numbers are meaningless on their own and a
-- reader checking this fixture should compare consecutive rows, never single values.
--
-- Samples are 30s apart, the everyChunk period JFR actually emits them on. The span under test runs
-- for 300ms at 10:10:00.000, so the window that overlaps it is opened 20s before it starts and
-- closed 10s after it ends -- which is precisely why the scan has to reach outside the trace in
-- both directions to find either sample.
--
-- The thread is irrelevant and deliberately so: this is a periodic JVM-wide sample, and the query
-- has no thread predicate at all.
INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.ContainerCPUThrottling', 'Container CPU Throttling', 30, 'cgroup cpu.stat', '["Operating System","Container"]', '1', NULL, false, NULL, NULL,
     '[{"field":"cpuElapsedSlices","header":"CFS Periods Elapsed"},{"field":"cpuThrottledSlices","header":"CFS Periods Throttled"},{"field":"cpuThrottledTime","header":"Throttled Time"}]');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The span under test: 10:10:00.000 .. 10:10:00.300 on thread 3001, the same one the pause
    -- fixture uses, so both read the same trace.
    ('jeffrey.TraceSpan', '2025-01-15T10:10:00.000Z', 600000, 300000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9002,"spanId":601,"parentSpanId":0,"name":"reserveInventory","kind":"SERVER","status":"UNSET"}'),

    -- S0, the baseline. It already carries throttling from before the recording began, which is
    -- exactly why it can never be a window of its own: 100 throttled periods here say nothing about
    -- anything that happened while we were watching.
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:09:40.000Z', 580000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":1000,"cpuThrottledSlices":100,"cpuThrottledTime":5000000000}'),

    -- S1 closes the window that contains the span: 300 CFS periods elapsed, 54 of them throttled
    -- (18%), 430ms parked somewhere inside those 30 seconds. Where inside is not recoverable, which
    -- is the whole reason this is drawn as a window rather than as a pause.
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:10:10.000Z', 610000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":1300,"cpuThrottledSlices":154,"cpuThrottledTime":5430000000}'),

    -- S2 closes a window in which the container was never throttled: the throttled counters stand
    -- still while the elapsed one advances. A band here would assert throttling that did not happen.
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:10:40.000Z', 640000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":1600,"cpuThrottledSlices":154,"cpuThrottledTime":5430000000}'),

    -- S3 closes a small but real window: 6 periods, 20ms.
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:11:10.000Z', 670000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":1900,"cpuThrottledSlices":160,"cpuThrottledTime":5450000000}'),

    -- S4 is a restarted container: every counter is lower than the sample before it. How much of
    -- the throttling preceded the reset cannot be recovered, so the window is dropped rather than
    -- clamped to zero -- a clamped one would report a number that is merely wrong instead of absent.
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:11:40.000Z', 700000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":200,"cpuThrottledSlices":3,"cpuThrottledTime":100000000}'),

    -- S5 pairs with S4 normally: the counters resume advancing from their new base. 10 periods, 300ms.
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:12:10.000Z', 730000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":500,"cpuThrottledSlices":13,"cpuThrottledTime":400000000}'),

    -- S6 and S7 are a container with no CFS quota, which is how the JVM writes these: all three
    -- counters null. It cannot be throttled, so it must produce no windows without anyone having to
    -- consult its ContainerConfiguration.
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:13:00.000Z', 780000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":null,"cpuThrottledSlices":null,"cpuThrottledTime":null}'),
    ('jdk.ContainerCPUThrottling', '2025-01-15T10:13:30.000Z', 810000, NULL, 1, NULL, NULL, NULL, 3001,
     '{"cpuElapsedSlices":null,"cpuThrottledSlices":null,"cpuThrottledTime":null}');
