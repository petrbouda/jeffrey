--
-- PERSISTENT QUEUE TABLES
--

CREATE SEQUENCE IF NOT EXISTS persistent_queue_seq START 1;

CREATE TABLE IF NOT EXISTS persistent_queue_events
(
    offset_id  BIGINT DEFAULT nextval('persistent_queue_seq') PRIMARY KEY,
    queue_name VARCHAR NOT NULL,
    scope_id   VARCHAR NOT NULL,
    dedup_key  VARCHAR,
    payload    VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_persistent_queue_events_scope ON persistent_queue_events(queue_name, scope_id, offset_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_persistent_queue_events_dedup ON persistent_queue_events(queue_name, scope_id, dedup_key);
CREATE INDEX IF NOT EXISTS idx_persistent_queue_events_created_at ON persistent_queue_events(created_at);

CREATE TABLE IF NOT EXISTS persistent_queue_consumers
(
    consumer_id       VARCHAR NOT NULL,
    queue_name        VARCHAR NOT NULL,
    scope_id          VARCHAR NOT NULL,
    last_offset       BIGINT DEFAULT 0,
    last_execution_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_id, queue_name, scope_id)
);
