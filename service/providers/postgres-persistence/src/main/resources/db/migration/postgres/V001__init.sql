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
CREATE TABLE IF NOT EXISTS projects
(
    project_id              TEXT    NOT NULL,
    origin_project_id       TEXT,
    project_name            TEXT    NOT NULL,
    workspace_id            TEXT    NOT NULL,
    created_at              BIGINT  NOT NULL,
    origin_created_at       BIGINT,
    attributes              TEXT    NOT NULL,
    graph_visualization     TEXT    NOT NULL,
    PRIMARY KEY (project_id)
);

CREATE INDEX IF NOT EXISTS idx_projects_workspace_id ON projects(workspace_id);

CREATE TABLE IF NOT EXISTS schedulers
(
    id         TEXT    NOT NULL,
    project_id TEXT,
    job_type   TEXT    NOT NULL,
    params     TEXT    NOT NULL,
    enabled    BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS repositories
(
    project_id                      TEXT NOT NULL,
    id                              TEXT NOT NULL,
    type                            TEXT NOT NULL,
    finished_session_detection_file TEXT,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS recordings
(
    project_id            TEXT   NOT NULL,
    id                    TEXT   NOT NULL,
    recording_name        TEXT   NOT NULL,
    folder_id             TEXT,
    event_source          TEXT   NOT NULL,
    created_at            BIGINT NOT NULL,
    recording_started_at  BIGINT NOT NULL,
    recording_finished_at BIGINT NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS recording_files
(
    project_id     TEXT   NOT NULL,
    recording_id   TEXT   NOT NULL,
    id             TEXT   NOT NULL,
    filename       TEXT   NOT NULL,
    supported_type TEXT   NOT NULL,
    uploaded_at    BIGINT NOT NULL,
    size_in_bytes  BIGINT NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE INDEX IF NOT EXISTS idx_recording_files_recording_id ON recording_files(project_id, recording_id);

CREATE TABLE IF NOT EXISTS recording_folders
(
    project_id TEXT NOT NULL,
    id         TEXT NOT NULL,
    name       TEXT NOT NULL,
    PRIMARY KEY (project_id, id)
);

--
-- PROFILE TABLES - COMMON
--
CREATE TABLE IF NOT EXISTS profiles
(
    profile_id            TEXT   NOT NULL,
    project_id            TEXT   NOT NULL,
    profile_name          TEXT   NOT NULL,
    event_source          TEXT   NOT NULL,
    created_at            BIGINT NOT NULL,
    recording_id          TEXT   NOT NULL,
    recording_started_at  BIGINT NOT NULL,
    recording_finished_at BIGINT NOT NULL,
    initialized_at        BIGINT,
    enabled_at            BIGINT,
    PRIMARY KEY (profile_id)
);

CREATE TABLE IF NOT EXISTS saved_graphs
(
    profile_id  TEXT   NOT NULL,
    id          TEXT   NOT NULL,
    name        TEXT   NULL,
    params      BYTEA  NOT NULL,
    content     BYTEA  NOT NULL,
    created_at  BIGINT NOT NULL,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE IF NOT EXISTS cache
(
    profile_id  TEXT  NOT NULL,
    key         TEXT  NOT NULL,
    content     BYTEA NOT NULL,
    PRIMARY KEY (profile_id, key)
);

--
-- PROFILE TABLES - EVENTS
--

CREATE TABLE IF NOT EXISTS event_types
(
    profile_id      TEXT    NOT NULL,
    name            TEXT    NOT NULL,
    label           TEXT    NOT NULL,
    type_id         BIGINT,
    description     TEXT,
    categories      TEXT,
    source          TEXT    NOT NULL,
    subtype         TEXT,
    samples         BIGINT  NOT NULL,
    weight          BIGINT,
    has_stacktrace  BOOLEAN NOT NULL,
    calculated      BOOLEAN NOT NULL,
    extras          TEXT,
    settings        TEXT,
    columns         TEXT,
    PRIMARY KEY (profile_id, name)
);

CREATE TABLE IF NOT EXISTS events
(
    profile_id                     TEXT   NOT NULL,
    event_id                       BIGINT NOT NULL,
    event_type                     TEXT   NOT NULL,
    start_timestamp                BIGINT NOT NULL,
    start_timestamp_from_beginning BIGINT NOT NULL,
    duration                       BIGINT,
    samples                        BIGINT NOT NULL,
    weight                         BIGINT,
    weight_entity                  TEXT,
    stacktrace_id                  BIGINT,
    thread_id                      BIGINT,
    fields                         JSONB,
    PRIMARY KEY (profile_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_events_event_type_start_timestamp_from_beginning ON events(profile_id, event_type, start_timestamp_from_beginning, stacktrace_id);
-- To effectively process calculated events (NativeLeaks - stores address as weight_entity)
CREATE INDEX IF NOT EXISTS idx_events_event_type_weight_entity ON events(profile_id, event_type, weight_entity);

CREATE TABLE IF NOT EXISTS stacktraces
(
    profile_id    TEXT   NOT NULL,
    stacktrace_id BIGINT NOT NULL,
    type_id       BIGINT NOT NULL,
    frames        TEXT   NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id)
);
CREATE INDEX IF NOT EXISTS idx_stacktraces_lookup ON stacktraces(profile_id, stacktrace_id);

CREATE TABLE IF NOT EXISTS stacktrace_tags
(
    profile_id    TEXT   NOT NULL,
    stacktrace_id BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id, tag_id)
);

CREATE TABLE IF NOT EXISTS threads
(
    profile_id TEXT    NOT NULL,
    thread_id  BIGINT  NOT NULL,
    name       TEXT    NOT NULL,
--     virtual threads does not have os_id
    os_id      BIGINT,
    java_id    BIGINT,
    is_virtual BOOLEAN NOT NULL,
    PRIMARY KEY (profile_id, thread_id)
);

CREATE TABLE IF NOT EXISTS workspaces
(
    workspace_id  TEXT PRIMARY KEY,
    repository_id TEXT,
    name          TEXT    NOT NULL,
    description   TEXT,
    location      TEXT,
    base_location TEXT,
    created_at    BIGINT  NOT NULL,
    type          TEXT    NOT NULL,
    deleted       BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS workspace_sessions
(
    session_id           TEXT   NOT NULL,
    origin_session_id    TEXT   NOT NULL,
    project_id           TEXT   NOT NULL,
    workspace_id         TEXT   NOT NULL,
    last_detected_file   TEXT,
    relative_path        TEXT   NOT NULL,
    workspaces_path      TEXT,
    origin_created_at    BIGINT NOT NULL,
    created_at           BIGINT NOT NULL,
    PRIMARY KEY (project_id, session_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_sessions_workspace_origin ON workspace_sessions(workspace_id, origin_session_id);

CREATE TABLE IF NOT EXISTS workspace_events
(
    event_id          BIGSERIAL PRIMARY KEY,
    origin_event_id   TEXT   NOT NULL,
    project_id        TEXT   NOT NULL,
    workspace_id      TEXT   NOT NULL,
    event_type        TEXT   NOT NULL,
    content           TEXT   NOT NULL,
    origin_created_at BIGINT NOT NULL,
    created_at        BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_events_project_origin ON workspace_events(project_id, origin_event_id);

CREATE TABLE IF NOT EXISTS workspace_event_consumers
(
    consumer_id       TEXT PRIMARY KEY,
    workspace_id      TEXT,
    last_offset       BIGINT,
    last_execution_at BIGINT,
    created_at        BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS profiler_settings
(
    profiler_id     TEXT PRIMARY KEY,
    workspace_id    TEXT,
    project_id      TEXT,
    agent_settings  TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_profiler_settings ON profiler_settings(workspace_id, project_id);
