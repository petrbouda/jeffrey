-- Event types that declare their own naming convention in the recording's metadata, the way a
-- @SpanName-annotated event does. The acme type is not a jeffrey.* type: this fixture is the proof
-- that an event type Jeffrey has never seen is named with no change to Jeffrey, which is the whole
-- point of carrying the convention in the recording.
--
-- The acme events are the plain-commit() shape -- kind and status carry their field-initializer
-- defaults, and there is no `name` key at all, because describeSpan() never ran. The name derives
-- from the declared template. The verdict deliberately does not: a verdict is the writer's
-- statement, recorded through commitSpan()/failed(), and an event that recorded none has none.
--
-- Loaded on its own.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('com.acme.KafkaPublish', 'Kafka Publish', 1, 'third-party publish', '["Application"]', '1', NULL, false,
     '{"spanName":"PUBLISH {topic}"}', NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"topic","header":"Topic"},{"field":"deliveryCode","header":"Delivery Code"}]'),

    -- The shape of a Jeffrey statement in a new recording: the identity template @SpanName("{name}")
    -- declares that the event names itself. The declared arm must agree with the recorded-name
    -- fallback it shadows -- the template exists for the invariant, not to change any answer.
    ('jeffrey.JdbcQuery', 'JDBC Query', 2, 'jdbc', '["Application","JDBC"]', '1', NULL, false,
     '{"spanName":"{name}"}', NULL,
     '[{"field":"traceId","header":"Trace Id"},{"field":"spanId","header":"Span Id"},{"field":"parentSpanId","header":"Parent Span Id"},{"field":"name","header":"Statement Name"},{"field":"kind","header":"Kind"},{"field":"status","header":"Status"},{"field":"sql","header":"SQL Query"}]');

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (6001, 'acme-worker-1', 91, 51, false);

INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- The headline case: plain commit(), no name recorded, named by the declared template. The 503
    -- in deliveryCode is NOT read as a failure -- no verdict was recorded, so none exists.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.000Z',   0, 12000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":701,"spanId":7011,"parentSpanId":0,"kind":"INTERNAL","status":"UNSET","topic":"orders","deliveryCode":503}'),
    -- The same operation again: one operation, two traces.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.100Z', 100,  8000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":702,"spanId":7021,"parentSpanId":0,"kind":"INTERNAL","status":"UNSET","topic":"orders","deliveryCode":201}'),
    -- A stale recorded name: the declared template must outrank it.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.200Z', 200,  9000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":703,"spanId":7031,"parentSpanId":0,"name":"old-spelling","kind":"INTERNAL","status":"UNSET","topic":"payments","deliveryCode":200}'),
    -- A recorded ERROR: the one way a third-party event states a failure, on any commit path.
    ('com.acme.KafkaPublish', '2025-01-15T10:00:00.300Z', 300,  7000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":704,"spanId":7041,"parentSpanId":0,"kind":"INTERNAL","status":"ERROR","errorType":"com.acme.PublishException","topic":"orders","deliveryCode":200}'),

    -- A statement under the identity template: the declared name is the recorded name.
    ('jeffrey.JdbcQuery', '2025-01-15T10:00:00.700Z', 700,  2000000, 1, NULL, NULL, NULL, 6001,
     '{"traceId":708,"spanId":7081,"parentSpanId":0,"name":"listSpans","kind":"CLIENT","status":"UNSET","sql":"SELECT * FROM spans"}');
