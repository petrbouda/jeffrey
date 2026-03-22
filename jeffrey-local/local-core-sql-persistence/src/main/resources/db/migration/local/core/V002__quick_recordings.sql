-- Quick Analysis Groups (folders for organizing recordings)
CREATE TABLE IF NOT EXISTS quick_groups (
    group_id   VARCHAR NOT NULL PRIMARY KEY,
    group_name VARCHAR NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

-- Quick Analysis Recordings (uploaded files, analyzed on demand)
CREATE TABLE IF NOT EXISTS quick_recordings (
    recording_id          VARCHAR NOT NULL PRIMARY KEY,
    filename              VARCHAR NOT NULL,
    group_id              VARCHAR REFERENCES quick_groups(group_id),
    event_source          VARCHAR NOT NULL,
    file_path             VARCHAR NOT NULL,
    size_in_bytes         BIGINT NOT NULL,
    uploaded_at           TIMESTAMPTZ NOT NULL,
    profiling_started_at  TIMESTAMPTZ,
    profiling_finished_at TIMESTAMPTZ,
    profile_id            VARCHAR
);

CREATE INDEX IF NOT EXISTS idx_quick_recordings_group ON quick_recordings(group_id);
