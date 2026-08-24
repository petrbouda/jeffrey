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
