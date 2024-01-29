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
    id         TEXT PRIMARY KEY,
    profile_id TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    created_at INTEGER NOT NULL,
    content    BLOB    NOT NULL
);

CREATE TABLE IF NOT EXISTS main.flamegraphs_predefined
(
    profile_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    content    BLOB NOT NULL,
    PRIMARY KEY (profile_id, event_type)
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

CREATE TABLE IF NOT EXISTS main.profile_information
(
    profile_id TEXT PRIMARY KEY,
    content    BLOB    NOT NULL,
    CONSTRAINT info_type UNIQUE (profile_id)
);
