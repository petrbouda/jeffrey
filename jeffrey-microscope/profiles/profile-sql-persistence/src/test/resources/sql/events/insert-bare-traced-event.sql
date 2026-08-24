-- An event type that carries trace identity and nothing else: no name, no kind, no status.
--
-- This is what third-party instrumentation looks like when it stamps only the ids, and what a
-- recording made before the span shape existed looks like. It belongs in the trace regardless, so
-- the derivation falls back to the event type for a name and to the neutral kind and status.
--
-- Loaded on top of insert-trace-spans.sql, joining the slow trace as a child of its root span.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jeffrey.ThirdPartyEvent', 'Third Party Event', 5, 'stamped by someone else', '["Application"]', '1', NULL, false, NULL, NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"payload","header":"Payload"}]');

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    ('jeffrey.ThirdPartyEvent', '2025-01-15T10:00:00.040Z', 40, 3000000, 1, NULL, NULL, NULL, 3001,
     '{"traceId":9223372036854775807,"spanId":444,"parentSpanId":111,"payload":"opaque"}');
