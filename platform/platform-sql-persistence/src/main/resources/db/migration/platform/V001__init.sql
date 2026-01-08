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
-- PLATFORM DATABASE SCHEMA
-- Contains platform/management tables. Profile event data is stored in per-profile databases.
--

--
-- PROJECT TABLES
--
CREATE TABLE IF NOT EXISTS projects
(
    project_id              VARCHAR NOT NULL,
    origin_project_id       VARCHAR,
    project_name            VARCHAR NOT NULL,
    project_label           VARCHAR,
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
-- PROFILE METADATA TABLE
-- Note: Profile event data (events, stacktraces, frames, threads, cache) is stored in per-profile databases.
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
    enabled_at            TIMESTAMPTZ,
    PRIMARY KEY (profile_id)
);

--
-- WORKSPACE TABLES
--

CREATE TABLE IF NOT EXISTS workspaces
(
    workspace_id        VARCHAR PRIMARY KEY,
    workspace_origin_id VARCHAR,
    repository_id       VARCHAR,
    name                VARCHAR NOT NULL,
    description         VARCHAR,
    location            VARCHAR,
    base_location       VARCHAR,
    created_at          TIMESTAMPTZ NOT NULL,
    type                VARCHAR NOT NULL,
    deleted             BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS repositories
(
    project_id               VARCHAR NOT NULL,
    repository_id            VARCHAR NOT NULL,
    repository_type          VARCHAR NOT NULL,
    workspaces_path          VARCHAR,
    relative_workspace_path  VARCHAR NOT NULL,
    relative_project_path    VARCHAR NOT NULL,
    PRIMARY KEY (project_id, repository_id)
);

CREATE TABLE IF NOT EXISTS repository_sessions
(
    session_id            VARCHAR NOT NULL,
    repository_id         VARCHAR NOT NULL,
    relative_session_path VARCHAR NOT NULL,
    profiler_settings     VARCHAR,
    finished_file         VARCHAR,
    streaming_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    origin_created_at     TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    finished_at           TIMESTAMPTZ,
    PRIMARY KEY (repository_id, session_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_repository_sessions_session_path ON repository_sessions(repository_id, relative_session_path);

CREATE TABLE IF NOT EXISTS workspace_events
(
    event_id          BIGINT PRIMARY KEY,
    origin_event_id   VARCHAR NOT NULL,
    project_id        VARCHAR NOT NULL,
    workspace_id      VARCHAR NOT NULL,
    event_type        VARCHAR NOT NULL,
    content           VARCHAR NOT NULL,
    origin_created_at TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_events_project_origin ON workspace_events(project_id, origin_event_id, event_type);

CREATE TABLE IF NOT EXISTS workspace_event_consumers
(
    consumer_id       VARCHAR PRIMARY KEY,
    workspace_id      VARCHAR,
    last_offset       BIGINT,
    last_execution_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS profiler_settings
(
    workspace_id    VARCHAR,
    project_id      VARCHAR,
    agent_settings  VARCHAR NOT NULL,
    UNIQUE (workspace_id, project_id)
);
