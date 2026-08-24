-- Fixture for DuckDBThreadWindowQueryTest.
-- jdk.SocketRead events on a pool of workers, clustered into a few milliseconds, to prove that the
-- tooltip's window lookup answers about the slice of time it was asked about.
--
-- start_timestamp_from_beginning is what every relative time filter runs on, and it is the column
-- most fixtures leave NULL — a window query against a NULL column matches nothing, so it is set
-- here deliberately.
--
-- The pool mixes both kinds of identity: two workers carry a Java id, one never got one and reports
-- -1, which is how a native thread appears. A collapsed lane has to match both.

INSERT INTO event_types (name, label, type_id, description, categories, source, subtype, has_stacktrace, extras, settings, columns)
VALUES
    ('jdk.SocketRead', 'Socket Read', 3, 'Reading data from a socket', '["Java Application"]', '1', NULL, false, NULL, NULL, NULL);

INSERT INTO threads (thread_hash, name, os_id, java_id, is_virtual)
VALUES
    (4001, 'http-nio-8080-exec-1', 51, 21, false),
    (4002, 'http-nio-8080-exec-2', 52, 22, false),
    (4003, 'http-nio-8080-exec-3', 53, -1, false),
    (4004, 'main', 54, 1, false);

INSERT INTO events_raw (event_type, start_timestamp, start_timestamp_from_beginning, duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
VALUES
    -- Millisecond 100: three events, one per pool worker. A window over this millisecond alone has
    -- to report three, not the lane's whole total.
    ('jdk.SocketRead', '2025-01-15T10:00:00.100Z', 100, 1658000, 1, NULL, NULL, NULL, 4001,
     '{"host":"db-1.internal","address":"10.23.88.52","port":1521,"bytesRead":1024,"endOfStream":false}'),
    ('jdk.SocketRead', '2025-01-15T10:00:00.100Z', 100, 1658000, 1, NULL, NULL, NULL, 4002,
     '{"host":"db-2.internal","address":"10.23.88.53","port":1521,"bytesRead":2048,"endOfStream":false}'),
    ('jdk.SocketRead', '2025-01-15T10:00:00.100Z', 100, 1658000, 1, NULL, NULL, NULL, 4003,
     '{"host":"db-3.internal","address":"10.23.88.54","port":1521,"bytesRead":4096,"endOfStream":false}'),
    -- Millisecond 101: one event, so two adjacent windows must not both claim it
    ('jdk.SocketRead', '2025-01-15T10:00:00.101Z', 101, 1658000, 1, NULL, NULL, NULL, 4001,
     '{"host":"db-1.internal","address":"10.23.88.52","port":1521,"bytesRead":512,"endOfStream":false}'),
    -- Millisecond 140: a later burst, far enough away to fall outside a window over 100..102
    ('jdk.SocketRead', '2025-01-15T10:00:00.140Z', 140, 1658000, 1, NULL, NULL, NULL, 4002,
     '{"host":"db-2.internal","address":"10.23.88.53","port":1521,"bytesRead":256,"endOfStream":false}'),
    -- Millisecond 900: the tail of the recording
    ('jdk.SocketRead', '2025-01-15T10:00:00.900Z', 900, 1658000, 1, NULL, NULL, NULL, 4001,
     '{"host":"db-1.internal","address":"10.23.88.52","port":1521,"bytesRead":128,"endOfStream":true}'),
    -- EXCLUDED from the pool: a thread outside the group, in the busiest millisecond
    ('jdk.SocketRead', '2025-01-15T10:00:00.100Z', 100, 1658000, 1, NULL, NULL, NULL, 4004,
     '{"host":"other.internal","address":"10.23.88.99","port":1521,"bytesRead":64,"endOfStream":false}');
