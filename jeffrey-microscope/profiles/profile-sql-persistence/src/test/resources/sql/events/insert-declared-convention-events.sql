-- Event types that declare their own span conventions in the recording's metadata, the way a
-- @SpanName/@SpanOutcome-annotated event does. None of them is a jeffrey.* type: this fixture is
-- the proof that an event type Jeffrey has never seen is named and judged with no change to
-- Jeffrey, which is the whole point of carrying the convention in the recording.
--
-- The events are the plain-commit() shape -- kind and status carry their field-initializer
-- defaults, and there is no `name` key at all, because describeSpan() never ran. Everything the
-- derivation knows comes from the declared template and outcome.
--
-- Loaded on its own.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    -- Declares both conventions.
    ('com.acme.KafkaPublish', 'Kafka Publish', 1, 'third-party publish', '["Application"]', '1', NULL, false,
     '{"spanName":"PUBLISH {topic}","spanOutcomeFrom":"deliveryCode","spanOutcomeSemantics":"HTTP_CODE"}', NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"topic","header":"Topic"},{"field":"deliveryCode","header":"Delivery Code"}]'),

    -- BOOLEAN semantics, the shape of a success flag.
    ('com.acme.CachePut', 'Cache Put', 2, 'third-party cache', '["Application"]', '1', NULL, false,
     '{"spanName":"CACHE PUT {region}","spanOutcomeFrom":"stored","spanOutcomeSemantics":"BOOLEAN"}', NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"region","header":"Region"},{"field":"stored","header":"Stored"}]'),

    -- A semantics this version of Jeffrey has never heard of: the declaration must be skipped, the
    -- event judged by what it recorded, and derive() must not fail.
    ('com.acme.FutureThing', 'Future Thing', 3, 'unknown semantics', '["Application"]', '1', NULL, false,
     '{"spanName":"FUTURE {what}","spanOutcomeFrom":"code","spanOutcomeSemantics":"QUANTUM_CODE"}', NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"what","header":"What"},{"field":"code","header":"Code"}]');

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (6001, 'acme-worker-1', 91, 51, false);

INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The headline case: plain commit(), no name recorded, code says the publish failed.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.000Z',   0, 12000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":701,"spanId":7011,"parentSpanId":0,"kind":"INTERNAL","status":"UNSET","topic":"orders","deliveryCode":503}'),
    -- The same operation succeeding: one operation, two traces, one failure.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.100Z', 100,  8000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":702,"spanId":7021,"parentSpanId":0,"kind":"INTERNAL","status":"UNSET","topic":"orders","deliveryCode":201}'),
    -- A stale recorded name: the declared template must outrank it.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.200Z', 200,  9000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":703,"spanId":7031,"parentSpanId":0,"name":"old-spelling","kind":"INTERNAL","status":"UNSET","topic":"payments","deliveryCode":200}'),
    -- Recorded ERROR beside a passing code: the escalation still outranks the declared outcome.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.300Z', 300,  7000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":704,"spanId":7041,"parentSpanId":0,"kind":"INTERNAL","status":"ERROR","errorType":"com.acme.PublishException","topic":"orders","deliveryCode":200}'),

    -- BOOLEAN semantics: a failed put and a successful one.
    ('com.acme.CachePut', '2025-01-15T10:00:00.400Z', 400,  3000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":705,"spanId":7051,"parentSpanId":0,"kind":"INTERNAL","status":"UNSET","region":"sessions","stored":false}'),
    ('com.acme.CachePut', '2025-01-15T10:00:00.500Z', 500,  2000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":706,"spanId":7061,"parentSpanId":0,"kind":"INTERNAL","status":"UNSET","region":"sessions","stored":true}'),

    -- Unknown semantics: still named by its template, judged only by what it recorded.
    ('com.acme.FutureThing', '2025-01-15T10:00:00.600Z', 600,  1000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":707,"spanId":7071,"parentSpanId":0,"kind":"INTERNAL","status":"UNSET","what":"entangle","code":"BAD"}');
