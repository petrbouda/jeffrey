-- Fixture for JdbcTraceRepositoryTest.BlockingSpanPromotion, layered on insert-trace-spans.sql
-- (which registers jeffrey.TraceSpan as a span type and creates threads 3001 and 3002).
--
-- One trace with a nested same-thread child span -- the shape no other fixture has, and the one
-- that actually exercises innermost attribution: a blocking event inside BOTH spans' windows must
-- land on the child, not the parent, and not on both.
--
-- Trace 9100 on thread 3002: exportReport (601) runs 11:00:00.000 .. .400, and loadRows (602) runs
-- .100 .. .300 inside it. The blocking events then cover every case the promotion decides:
--   inside both spans          -> promoted under the innermost (602)
--   inside the parent only     -> promoted under the parent (601), before and after the child
--   null duration              -> promoted with a duration of zero, not dropped
--   after every span ended     -> not promoted
--   on a thread with no span   -> not promoted
-- and one jdk.Deoptimization -- a context category that is NOT promoted -- proves the span-context
-- query attributes innermost too, and that the drill-down exclusion is selective.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.SocketRead', 'Socket Read', 30, 'socket read', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"host","header":"Remote Host"},{"field":"address","header":"Remote Address"},{"field":"port","header":"Remote Port"},{"field":"timeout","header":"Timeout Value"},{"field":"bytesRead","header":"Bytes Read"},{"field":"endOfStream","header":"End of Stream"}]'),
    ('jdk.FileRead', 'File Read', 31, 'file read', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"path","header":"Path"},{"field":"bytesRead","header":"Bytes Read"},{"field":"endOfFile","header":"End of File"}]'),
    ('jdk.ThreadPark', 'Thread Park', 32, 'park', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"parkedClass","header":"Class Parked On"}]'),
    ('jdk.JavaMonitorEnter', 'Java Monitor Blocked', 33, 'monitor', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"monitorClass","header":"Monitor Class"},{"field":"previousOwner","header":"Previous Owner"}]'),
    ('jdk.Deoptimization', 'Deoptimization', 34, 'deopt', '["Java Virtual Machine","Compiler"]', '1', NULL, true, NULL, NULL,
     '[{"field":"reason","header":"Reason"}]');

-- Stacks for the two class-loading probes below. ROOT-FIRST, like every stacktrace in this schema,
-- so the leaf -- the frame that actually read the file -- is the LAST element.
--
-- The pair exists to pin the one distinction the classification rests on: both read a .jar, and both
-- reach it through the same java.util.zip leaf frame. Only the frames underneath differ. A rule that
-- looked at the path, or at the leaf, would give these two the same answer and be wrong about one.
INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
VALUES
    (8101, 'java.lang.Thread', 'run', 'Interpreted', 1583, 0),
    -- The class-loading chain, exactly as JFR records it: Resource.getBytes is the leaf, and the
    -- loader frames sit a few frames underneath.
    (8102, 'jdk.internal.loader.BuiltinClassLoader', 'loadClass', 'JIT', 578, 4),
    (8103, 'jdk.internal.loader.BuiltinClassLoader', 'defineClass', 'JIT', 773, 12),
    (8104, 'jdk.internal.loader.Resource', 'getBytes', 'Interpreted', 106, 0),
    -- A library unpacking its own native library out of the very same jar. No loader frame anywhere.
    (8105, 'org.duckdb.DuckDBNative', 'loadNativeLibrary', 'Interpreted', 51, 0),
    (8106, 'org.duckdb.DuckDBNative', 'unpackAndLoad', 'Interpreted', 88, 21),
    (8107, 'java.util.zip.ZipFile$Source', 'readAt', 'JIT', 1289, 7);

INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
VALUES
    (7101, 100, [8101, 8102, 8103, 8104], []),
    (7102, 100, [8101, 8105, 8106, 8107], []);

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The two recorded spans: parent 601 and its same-thread child 602.
    ('jeffrey.TraceSpan', '2025-01-15T11:00:00.000Z', 3600000, 400000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":9100,"spanId":601,"parentSpanId":0,"name":"exportReport","kind":"SERVER","status":"UNSET"}'),
    ('jeffrey.TraceSpan', '2025-01-15T11:00:00.100Z', 3600100, 200000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":9100,"spanId":602,"parentSpanId":601,"name":"loadRows","kind":"INTERNAL","status":"UNSET"}'),

    -- Inside BOTH spans' windows: the innermost pick is what this row exists to prove. The
    -- startTime key is deliberate -- it is plumbing and must not survive into event_fields.
    ('jdk.SocketRead', '2025-01-15T11:00:00.150Z', 3600150, 50000000, 1, NULL, NULL, NULL, 3002,
     '{"startTime":123,"host":"db-primary","address":"10.0.0.5","port":5432,"timeout":30000,"bytesRead":8192,"endOfStream":false}'),

    -- Inside the parent only, before the child begins.
    ('jdk.FileRead', '2025-01-15T11:00:00.020Z', 3600020, 30000000, 1, NULL, NULL, NULL, 3002,
     '{"path":"/data/report.csv","bytesRead":65536,"endOfFile":false}'),

    -- A read JFR recorded with no duration at all: still a wait the trace should show, at zero
    -- length, rather than a row silently dropped.
    ('jdk.FileRead', '2025-01-15T11:00:00.050Z', 3600050, NULL, 1, NULL, NULL, NULL, 3002,
     '{"path":"/data/empty.bin","bytesRead":0,"endOfFile":true}'),

    -- Read by the class loader: a loader frame is on the stack, so this one is CLASS_LOADING.
    ('jdk.FileRead', '2025-01-15T11:00:00.030Z', 3600030, 1000000, 1, NULL, NULL, 7101, 3002,
     '{"path":"/app/lib/microscope.jar","bytesRead":10240,"endOfFile":false}'),

    -- The same jar, the same java.util.zip leaf frame, a library unpacking its own .so. NOT class
    -- loading -- and the row that fails if the classification ever regresses to matching on the path
    -- or on the leaf frame.
    ('jdk.FileRead', '2025-01-15T11:00:00.040Z', 3600040, 1000000, 1, NULL, NULL, 7102, 3002,
     '{"path":"/app/lib/microscope.jar","bytesRead":8192,"endOfFile":false}'),

    -- Inside the parent only, after the child has ended.
    ('jdk.ThreadPark', '2025-01-15T11:00:00.350Z', 3600350, 20000000, 1, NULL, NULL, NULL, 3002,
     '{"parkedClass":"java.util.concurrent.CompletableFuture$Signaller"}'),

    -- After every span of the trace ended: no parent window contains it, so nothing may promote it.
    ('jdk.SocketRead', '2025-01-15T11:00:00.900Z', 3600900, 10000000, 1, NULL, NULL, NULL, 3002,
     '{"host":"db-primary","address":"10.0.0.5","port":5432,"timeout":30000,"bytesRead":128,"endOfStream":false}'),

    -- On a thread no span of this window ever ran on.
    ('jdk.JavaMonitorEnter', '2025-01-15T11:00:00.150Z', 3600150, 40000000, 1, NULL, NULL, NULL, 3001,
     '{"monitorClass":"java.lang.Object","previousOwner":"worker-9"}'),

    -- Not promoted (an instant event stays context), inside both spans: the span-context query must
    -- attribute it to the innermost span only, and the drill-down must still list it.
    ('jdk.Deoptimization', '2025-01-15T11:00:00.160Z', 3600160, NULL, 1, NULL, NULL, NULL, 3002,
     '{"reason":"unstable_if"}');
