CREATE TABLE IF NOT EXISTS main.profiles
(
    id             TEXT PRIMARY KEY,
    name           TEXT    NOT NULL UNIQUE,
    created_at     INTEGER NOT NULL,
    started_at     INTEGER NOT NULL,
    recording_path TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS main.flamegraphs
(
    id              TEXT PRIMARY KEY,
    profile_id      TEXT    NOT NULL,
    event_type      TEXT    NOT NULL,
    graph_type      TEXT    NOT NULL,
    use_thread_mode BOOLEAN NULL,
    use_weight      BOOLEAN NULL,
    complete        BOOLEAN NULL,
    name            TEXT    NULL,
    created_at      INTEGER NOT NULL,
    content         BLOB    NOT NULL,
    CONSTRAINT predefined_constraint UNIQUE (profile_id, event_type, complete)
);

CREATE TABLE IF NOT EXISTS main.heatmaps
(
    id         TEXT PRIMARY KEY,
    profile_id TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    created_at INTEGER NOT NULL,
    content    BLOB    NOT NULL,
    CONSTRAINT heatmap_type UNIQUE (profile_id, name)
);

CREATE TABLE IF NOT EXISTS main.timeseries
(
    id         TEXT PRIMARY KEY,
    profile_id TEXT    NOT NULL,
    event_type TEXT    NOT NULL,
    created_at INTEGER NOT NULL,
    content    BLOB    NOT NULL,
    CONSTRAINT timeseries_type UNIQUE (profile_id, event_type)
);


CREATE TABLE IF NOT EXISTS main.profile_information
(
    profile_id TEXT PRIMARY KEY,
    content    BLOB NOT NULL,
    CONSTRAINT info_type UNIQUE (profile_id)
);
