-- Fixture for JdbcTraceRepositoryTest.PooledFields, layered on insert-trace-spans.sql (which
-- registers jeffrey.TraceSpan as a span type and creates threads 3001 and 3002).
--
-- Events whose largest field the parser lifted out of `fields` into field_texts, leaving
-- `pooled_field` + `pooled_text_hash` behind -- what every recording with long SQL, long paths or
-- long class names actually looks like on disk. Both kinds of span must come back carrying the
-- pooled value:
--   a recorded span   -- derived through the `events` view, which splices the text back
--   a promoted wait   -- derived off events_raw for its rowid, which has to splice it by hand
-- The second is the one that regressed: its payload hashed to a trace_span_payloads row nobody
-- wrote, so the waterfall drew the wait with none of its detail.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.JavaMonitorEnter', 'Java Monitor Blocked', 40, 'monitor', '["Java Application"]', '1', NULL, true, NULL, NULL,
     '[{"field":"monitorClass","header":"Monitor Class"},{"field":"previousOwner","header":"Previous Owner"}]');

-- The pooled texts. The hashes are arbitrary here -- the parser computes them from content, and
-- everything downstream only has to agree with itself.
INSERT INTO field_texts (text_hash, text)
VALUES
    (770001, 'SELECT r.id, r.name, r.created_at FROM report r JOIN report_definition rd ON rd.id = r.report_definition_id WHERE r.tenant_id = ? ORDER BY r.created_at DESC'),
    (770002, 'com.example.deeply.nested.internal.concurrent.SharedRegistryLock$ExclusiveSection');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields, pooled_field, pooled_text_hash)
VALUES
    -- Trace 9300 on thread 3002: one recorded statement span, whose `sql` was pooled.
    ('jeffrey.JdbcQuery', '2025-01-15T12:00:00.000Z', 7200000, 90000000, 1, NULL, NULL, NULL, 3002,
     '{"traceId":9300,"spanId":701,"parentSpanId":0,"name":"ReportsMapper.list","kind":"CLIENT","status":"UNSET","group":"ReportsMapper","rows":18}',
     'sql', 770001),

    -- A monitor wait inside that span's window, whose `monitorClass` was pooled. It is promoted to
    -- a synthesized leaf under span 701.
    ('jdk.JavaMonitorEnter', '2025-01-15T12:00:00.020Z', 7200020, 15000000, 1, NULL, NULL, NULL, 3002,
     '{"previousOwner":"worker-3"}', 'monitorClass', 770002);
