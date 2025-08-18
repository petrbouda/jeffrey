/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

--
-- PROJECT TABLES
--
CREATE TABLE IF NOT EXISTS main.projects
(
    project_id              TEXT    NOT NULL,
    origin_project_id       TEXT,
    project_name            TEXT    NOT NULL,
    workspace_id            TEXT,
    created_at              INTEGER NOT NULL,
    origin_created_at       INTEGER,
    attributes              TEXT    NOT NULL,
    graph_visualization     TEXT    NOT NULL,
    PRIMARY KEY (project_id)
);

CREATE INDEX idx_projects_workspace_id ON projects(workspace_id);

CREATE TABLE IF NOT EXISTS main.schedulers
(
    id         TEXT NOT NULL,
    project_id TEXT,
    job_type   TEXT NOT NULL,
    params     TEXT NOT NULL,
    enabled    BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS main.repositories
(
    project_id                      TEXT NOT NULL,
    id                              TEXT NOT NULL,
    type                            TEXT NOT NULL,
    finished_session_detection_file TEXT,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS main.recordings
(
    project_id            TEXT NOT NULL,
    id                    TEXT NOT NULL,
    recording_name        TEXT NOT NULL,
    folder_id             TEXT,
    event_source          TEXT NOT NULL,
    created_at            INTEGER NOT NULL,
    recording_started_at  INTEGER NOT NULL,
    recording_finished_at INTEGER NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS main.recording_files
(
    project_id     TEXT NOT NULL,
    recording_id   TEXT NOT NULL,
    id             TEXT NOT NULL,
    filename       TEXT NOT NULL,
    supported_type TEXT NOT NULL,
    uploaded_at    INTEGER NOT NULL,
    size_in_bytes  INTEGER NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE INDEX idx_recording_files_recording_id ON recording_files(project_id, recording_id);

CREATE TABLE IF NOT EXISTS main.recording_folders
(
    project_id TEXT NOT NULL,
    id         TEXT NOT NULL,
    name       TEXT NOT NULL,
    PRIMARY KEY (project_id, id)
);

--
-- PROFILE TABLES - COMMON
--
CREATE TABLE IF NOT EXISTS main.profiles
(
    profile_id            TEXT    NOT NULL,
    project_id            TEXT    NOT NULL,
    profile_name          TEXT    NOT NULL,
    event_source          TEXT    NOT NULL,
    event_fields_setting  TEXT    NOT NULL,
    created_at            INTEGER NOT NULL,
    recording_id          TEXT    NOT NULL,
    recording_started_at  INTEGER NOT NULL,
    recording_finished_at INTEGER NOT NULL,
    initialized_at        INTEGER,
    enabled_at            INTEGER,
    PRIMARY KEY (profile_id)
);

CREATE TABLE IF NOT EXISTS main.saved_graphs
(
    profile_id  TEXT    NOT NULL,
    id          TEXT    NOT NULL,
    name        TEXT    NULL,
    params      BLOB    NOT NULL,
    content     BLOB    NOT NULL,
    created_at  INTEGER NOT NULL,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE IF NOT EXISTS main.cache
(
    profile_id  TEXT NOT NULL,
    key         TEXT NOT NULL,
    content     BLOB NOT NULL,
    PRIMARY KEY (profile_id, key)
);

--
-- PROFILE TABLES - EVENTS
--

CREATE TABLE IF NOT EXISTS main.event_types
(
    profile_id      TEXT    NOT NULL,
    name            TEXT    NOT NULL,
    label           TEXT    NOT NULL,
    type_id         INTEGER,
    description     TEXT,
    categories      TEXT,
    source          TEXT    NOT NULL,
    subtype         TEXT,
    samples         INTEGER NOT NULL,
    weight          INTEGER,
    has_stacktrace  BOOLEAN NOT NULL,
    calculated      BOOLEAN NOT NULL,
    extras          TEXT,
    settings        TEXT,
    columns         TEXT,
    PRIMARY KEY (profile_id, name)
);

CREATE TABLE IF NOT EXISTS main.events
(
    profile_id                     TEXT    NOT NULL,
    event_id                       INTEGER NOT NULL,
    event_type                     TEXT    NOT NULL,
    start_timestamp                INTEGER NOT NULL,
    start_timestamp_from_beginning INTEGER NOT NULL,
    end_timestamp                  INTEGER,
    end_timestamp_from_beginning   INTEGER,
    duration                       INTEGER,
    samples                        INTEGER NOT NULL,
    weight                         INTEGER,
    weight_entity                  TEXT,
    stacktrace_id                  INTEGER,
    thread_id                      INTEGER,
    fields                         JSONB,
    PRIMARY KEY (profile_id, event_id)
);

CREATE INDEX idx_events_event_type_start_timestamp_from_beginning ON events(profile_id, event_type, start_timestamp_from_beginning);
-- To effectively process calculated events (NativeLeaks - stores address as weight_entity)
CREATE INDEX idx_events_event_type_weight_entity ON events(profile_id, event_type, weight_entity);

CREATE TABLE IF NOT EXISTS main.stacktraces
(
    profile_id    TEXT    NOT NULL,
    stacktrace_id INTEGER NOT NULL,
    type_id       INTEGER NOT NULL,
    frames        TEXT    NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id)
);

CREATE TABLE IF NOT EXISTS main.stacktrace_tags
(
    profile_id    TEXT    NOT NULL,
    stacktrace_id INTEGER NOT NULL,
    tag_id        INTEGER NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id, tag_id)
);

CREATE TABLE IF NOT EXISTS main.threads
(
    profile_id TEXT    NOT NULL,
    thread_id  TEXT    NOT NULL,
    name       TEXT    NOT NULL,
--     virtual threads does not have os_id
    os_id      INTEGER,
    java_id    INTEGER,
    is_virtual BOOLEAN NOT NULL,
    PRIMARY KEY (profile_id, thread_id)
);

CREATE TABLE IF NOT EXISTS main.workspaces
(
    workspace_id TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    description  TEXT,
    path         TEXT,
    enabled      BOOLEAN NOT NULL,
    created_at   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS main.workspace_sessions
(
    session_id           TEXT NOT NULL,
    origin_session_id    TEXT NOT NULL,
    project_id           TEXT NOT NULL,
    workspace_id         TEXT NOT NULL,
    last_detected_file   TEXT,
    relative_path        TEXT NOT NULL,
    workspaces_path      TEXT NOT NULL,
    origin_created_at    INTEGER NOT NULL,
    created_at           INTEGER NOT NULL,
    PRIMARY KEY (project_id, session_id)
);

CREATE UNIQUE INDEX idx_workspace_sessions_workspace_origin ON workspace_sessions(workspace_id, origin_session_id);

CREATE TABLE IF NOT EXISTS main.workspace_events
(
    event_id          INTEGER PRIMARY KEY,
    origin_event_id   TEXT NOT NULL,
    project_id        TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    event_type        TEXT NOT NULL,
    content           TEXT NOT NULL,
    origin_created_at INTEGER NOT NULL,
    created_at        INTEGER NOT NULL
);

CREATE UNIQUE INDEX idx_workspace_events_project_origin ON workspace_events(project_id, origin_event_id);

CREATE TABLE IF NOT EXISTS main.workspace_event_consumers
(
    consumer_id       TEXT PRIMARY KEY,
    last_offset       INTEGER,
    last_execution_at INTEGER,
    created_at        INTEGER NOT NULL
);
