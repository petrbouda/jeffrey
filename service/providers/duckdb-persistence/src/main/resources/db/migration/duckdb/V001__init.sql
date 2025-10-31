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
    project_id              VARCHAR NOT NULL,
    origin_project_id       VARCHAR,
    project_name            VARCHAR NOT NULL,
    workspace_id            VARCHAR NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    origin_created_at       TIMESTAMPTZ,
    attributes              VARCHAR NOT NULL,
    graph_visualization     VARCHAR NOT NULL,
    PRIMARY KEY (project_id)
);

CREATE INDEX IF NOT EXISTS idx_projects_workspace_id ON projects(workspace_id);

CREATE TABLE IF NOT EXISTS schedulers
(
    id         VARCHAR NOT NULL,
    project_id VARCHAR,
    job_type   VARCHAR NOT NULL,
    params     VARCHAR NOT NULL,
    enabled    BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS repositories
(
    project_id                      VARCHAR NOT NULL,
    id                              VARCHAR NOT NULL,
    type                            VARCHAR NOT NULL,
    finished_session_detection_file VARCHAR,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS recordings
(
    project_id            VARCHAR NOT NULL,
    id                    VARCHAR NOT NULL,
    recording_name        VARCHAR NOT NULL,
    folder_id             VARCHAR,
    event_source          VARCHAR NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    recording_started_at  TIMESTAMPTZ NOT NULL,
    recording_finished_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS recording_files
(
    project_id     VARCHAR NOT NULL,
    recording_id   VARCHAR NOT NULL,
    id             VARCHAR NOT NULL,
    filename       VARCHAR NOT NULL,
    supported_type VARCHAR NOT NULL,
    uploaded_at    TIMESTAMPTZ NOT NULL,
    size_in_bytes  BIGINT  NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE INDEX IF NOT EXISTS idx_recording_files_recording_id ON recording_files(project_id, recording_id);

CREATE TABLE IF NOT EXISTS recording_folders
(
    project_id VARCHAR NOT NULL,
    id         VARCHAR NOT NULL,
    name       VARCHAR NOT NULL,
    PRIMARY KEY (project_id, id)
);

--
-- PROFILE TABLES - COMMON
--
CREATE TABLE IF NOT EXISTS profiles
(
    profile_id            VARCHAR NOT NULL,
    project_id            VARCHAR NOT NULL,
    profile_name          VARCHAR NOT NULL,
    event_source          VARCHAR NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    recording_id          VARCHAR NOT NULL,
    recording_started_at  TIMESTAMPTZ NOT NULL,
    recording_finished_at TIMESTAMPTZ NOT NULL,
    initialized_at        TIMESTAMPTZ,
    enabled_at            TIMESTAMPTZ,
    PRIMARY KEY (profile_id)
);

CREATE TABLE IF NOT EXISTS saved_graphs
(
    profile_id  VARCHAR NOT NULL,
    id          VARCHAR NOT NULL,
    name        VARCHAR,
    params      BLOB    NOT NULL,
    content     BLOB    NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE IF NOT EXISTS cache
(
    profile_id  VARCHAR NOT NULL,
    key         VARCHAR NOT NULL,
    content     BLOB    NOT NULL,
    PRIMARY KEY (profile_id, key)
);

--
-- PROFILE TABLES - EVENTS (Optimized for DuckDB)
--

CREATE TABLE IF NOT EXISTS event_types
(
    profile_id      VARCHAR NOT NULL,
    name            VARCHAR NOT NULL,
    label           VARCHAR NOT NULL,
    type_id         BIGINT,
    description     VARCHAR,
    categories      VARCHAR,
    source          VARCHAR NOT NULL,
    subtype         VARCHAR,
    samples         BIGINT  NOT NULL,
    weight          BIGINT,
    has_stacktrace  BOOLEAN NOT NULL,
    calculated      BOOLEAN NOT NULL,
    extras          VARCHAR,
    settings        VARCHAR,
    columns         VARCHAR,
    PRIMARY KEY (profile_id, name)
);

CREATE TABLE IF NOT EXISTS frames
(
    profile_id      VARCHAR NOT NULL,
    frame_hash      BIGINT NOT NULL,
    class_name      VARCHAR,
    method_name     VARCHAR,
    frame_type      VARCHAR, -- JIT/Interpreted/Native/C++
    line_number     INTEGER,
    bytecode_index  INTEGER,
    PRIMARY KEY (profile_id, frame_hash)
);

CREATE TABLE IF NOT EXISTS stacktraces
(
    profile_id      VARCHAR NOT NULL,
    stack_hash      BIGINT NOT NULL,    -- Hash of frame_hashes array for deduplication
    type_id         INTEGER NOT NULL,   -- Numerical representation of the stacktrace type
    frame_hashes    BIGINT[],           -- Array of references to frames table
    tag_ids         INTEGER[],          -- Array of tags for categorization and filtering
    PRIMARY KEY (profile_id, stack_hash)
);

CREATE TABLE IF NOT EXISTS events
(
    profile_id                     VARCHAR,
    event_id                       BIGINT,
    event_type                     VARCHAR,
    start_timestamp                TIMESTAMPTZ NOT NULL,
    start_timestamp_from_beginning BIGINT NOT NULL,
    duration                       BIGINT,
    samples                        BIGINT NOT NULL,
    weight                         BIGINT,
    weight_entity                  VARCHAR,
    stack_hash                     BIGINT,  -- Reference to stacktraces.stack_hash
    thread_id                      BIGINT,  -- Hash value
    fields                         JSON,     -- JSON fields for event-specific data
    PRIMARY KEY (profile_id, event_id)
);

-- Optimized indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_events_stack_hash ON events(stack_hash);
CREATE INDEX IF NOT EXISTS idx_events_composite ON events(profile_id, event_type, start_timestamp_from_beginning);
-- To effectively process calculated events (NativeLeaks - stores address as weight_entity)
CREATE INDEX IF NOT EXISTS idx_events_event_type_weight_entity ON events(profile_id, event_type, weight_entity);

CREATE TABLE IF NOT EXISTS threads
(
    profile_id VARCHAR  NOT NULL,
    thread_id  BIGINT   NOT NULL,
    name       VARCHAR  NOT NULL,
    -- virtual threads does not have os_id
    os_id      BIGINT,
    java_id    BIGINT,
    is_virtual BOOLEAN  NOT NULL,
    PRIMARY KEY (profile_id, thread_id)
);

--
-- WORKSPACE TABLES
--

CREATE TABLE IF NOT EXISTS workspaces
(
    workspace_id  VARCHAR PRIMARY KEY,
    repository_id VARCHAR,
    name          VARCHAR NOT NULL,
    description   VARCHAR,
    location      VARCHAR,
    base_location VARCHAR,
    created_at    TIMESTAMPTZ NOT NULL,
    type          VARCHAR NOT NULL,
    deleted       BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS workspace_sessions
(
    session_id           VARCHAR NOT NULL,
    origin_session_id    VARCHAR NOT NULL,
    project_id           VARCHAR NOT NULL,
    workspace_id         VARCHAR NOT NULL,
    last_detected_file   VARCHAR,
    relative_path        VARCHAR NOT NULL,
    workspaces_path      VARCHAR,
    origin_created_at    TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (project_id, session_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_sessions_workspace_origin ON workspace_sessions(workspace_id, origin_session_id);

CREATE TABLE IF NOT EXISTS workspace_events
(
    event_id          BIGINT PRIMARY KEY,
    origin_event_id   VARCHAR NOT NULL,
    project_id        VARCHAR NOT NULL,
    workspace_id      VARCHAR NOT NULL,
    event_type        VARCHAR NOT NULL,
    content           VARCHAR NOT NULL,
    origin_created_at TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_events_project_origin ON workspace_events(project_id, origin_event_id);

CREATE TABLE IF NOT EXISTS workspace_event_consumers
(
    consumer_id       VARCHAR PRIMARY KEY,
    workspace_id      VARCHAR,
    last_offset       BIGINT,
    last_execution_at BIGINT,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS profiler_settings
(
    profiler_id     VARCHAR PRIMARY KEY,
    workspace_id    VARCHAR,
    project_id      VARCHAR,
    agent_settings  VARCHAR NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_profiler_settings ON profiler_settings(workspace_id, project_id);
