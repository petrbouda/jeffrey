CREATE TABLE IF NOT EXISTS main.profiles
(
    id           TEXT PRIMARY KEY,
    name         TEXT    NOT NULL UNIQUE,
    created_at   INTEGER NOT NULL,
    profile_path TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS main.flamegraphs
(
    id         TEXT PRIMARY KEY,
    profile_id TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    created_at INTEGER NOT NULL,
    content    BLOB    NOT NULL
);

CREATE TABLE IF NOT EXISTS main.heatmaps
(
    id         TEXT PRIMARY KEY,
    profile_id TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    created_at INTEGER NOT NULL,
    content    BLOB    NOT NULL
);
