/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
-- PLATFORM DATABASE SCHEMA (Turso / libsql)
-- Contains platform/management tables. Profile event data is stored in per-profile databases.
-- Timestamps stored as INTEGER (epoch microseconds).
--

--
-- PROJECT TABLES
--
CREATE TABLE IF NOT EXISTS projects
(
    project_id          TEXT    NOT NULL,
    origin_project_id   TEXT,
    project_name        TEXT    NOT NULL,
    project_label       TEXT,
    namespace           TEXT,
    workspace_id        TEXT    NOT NULL,
    created_at          INTEGER NOT NULL,
    origin_created_at   INTEGER,
    attributes          TEXT    NOT NULL,
    graph_visualization TEXT    NOT NULL,
    PRIMARY KEY (project_id)
);

CREATE INDEX IF NOT EXISTS idx_projects_workspace_id ON projects(workspace_id);
CREATE INDEX IF NOT EXISTS idx_projects_namespace ON projects(namespace);

CREATE TABLE IF NOT EXISTS schedulers
(
    id         TEXT    NOT NULL,
    project_id TEXT,
    job_type   TEXT    NOT NULL,
    params     TEXT    NOT NULL,
    enabled    INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS recordings
(
    project_id            TEXT    NOT NULL,
    id                    TEXT    NOT NULL,
    recording_name        TEXT    NOT NULL,
    folder_id             TEXT,
    event_source          TEXT    NOT NULL,
    created_at            INTEGER NOT NULL,
    recording_started_at  INTEGER NOT NULL,
    recording_finished_at INTEGER NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS recording_files
(
    project_id     TEXT    NOT NULL,
    recording_id   TEXT    NOT NULL,
    id             TEXT    NOT NULL,
    filename       TEXT    NOT NULL,
    supported_type TEXT    NOT NULL,
    uploaded_at    INTEGER NOT NULL,
    size_in_bytes  INTEGER NOT NULL,
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
-- PROFILE METADATA TABLE
-- Note: Profile event data (events, stacktraces, frames, threads, cache) is stored in per-profile databases.
--
CREATE TABLE IF NOT EXISTS profiles
(
    profile_id            TEXT    NOT NULL,
    project_id            TEXT    NOT NULL,
    profile_name          TEXT    NOT NULL,
    event_source          TEXT    NOT NULL,
    created_at            INTEGER NOT NULL,
    recording_id          TEXT    NOT NULL,
    recording_started_at  INTEGER NOT NULL,
    recording_finished_at INTEGER NOT NULL,
    enabled_at            INTEGER,
    PRIMARY KEY (profile_id)
);

--
-- WORKSPACE TABLES
--

CREATE TABLE IF NOT EXISTS workspaces
(
    workspace_id        TEXT PRIMARY KEY,
    workspace_origin_id TEXT,
    repository_id       TEXT,
    name                TEXT    NOT NULL,
    description         TEXT,
    location            TEXT,
    base_location       TEXT,
    created_at          INTEGER NOT NULL,
    type                TEXT    NOT NULL,
    deleted             INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS repositories
(
    project_id              TEXT NOT NULL,
    repository_id           TEXT NOT NULL,
    repository_type         TEXT NOT NULL,
    workspaces_path         TEXT,
    relative_workspace_path TEXT NOT NULL,
    relative_project_path   TEXT NOT NULL,
    PRIMARY KEY (project_id, repository_id)
);

CREATE TABLE IF NOT EXISTS project_instance_sessions
(
    session_id            TEXT    NOT NULL,
    repository_id         TEXT    NOT NULL,
    instance_id           TEXT    NOT NULL,
    session_order         INTEGER NOT NULL DEFAULT 1,
    relative_session_path TEXT    NOT NULL,
    profiler_settings     TEXT,
    origin_created_at     INTEGER NOT NULL,
    created_at            INTEGER NOT NULL,
    finished_at           INTEGER,
    PRIMARY KEY (repository_id, session_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_instance_sessions_session_path ON project_instance_sessions(repository_id, relative_session_path);
CREATE INDEX IF NOT EXISTS idx_project_instance_sessions_instance_id ON project_instance_sessions(instance_id);

--
-- PERSISTENT QUEUE TABLES
-- Uses AUTOINCREMENT instead of DuckDB sequences.
--

CREATE TABLE IF NOT EXISTS persistent_queue_events
(
    offset_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    queue_name TEXT    NOT NULL,
    scope_id   TEXT    NOT NULL,
    dedup_key  TEXT,
    payload    TEXT    NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_persistent_queue_events_scope ON persistent_queue_events(queue_name, scope_id, offset_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_persistent_queue_events_dedup ON persistent_queue_events(queue_name, scope_id, dedup_key);
CREATE INDEX IF NOT EXISTS idx_persistent_queue_events_created_at ON persistent_queue_events(created_at);

CREATE TABLE IF NOT EXISTS persistent_queue_consumers
(
    consumer_id       TEXT    NOT NULL,
    queue_name        TEXT    NOT NULL,
    scope_id          TEXT    NOT NULL,
    last_offset       INTEGER DEFAULT 0,
    last_execution_at INTEGER,
    created_at        INTEGER NOT NULL,
    PRIMARY KEY (consumer_id, queue_name, scope_id)
);

CREATE TABLE IF NOT EXISTS profiler_settings
(
    workspace_id   TEXT,
    project_id     TEXT,
    agent_settings TEXT NOT NULL,
    UNIQUE (workspace_id, project_id)
);

--
-- PROJECT INSTANCE TABLES
--
CREATE TABLE IF NOT EXISTS project_instances
(
    instance_id TEXT    NOT NULL,
    project_id  TEXT    NOT NULL,
    hostname    TEXT    NOT NULL,
    started_at  INTEGER NOT NULL,
    PRIMARY KEY (instance_id)
);

CREATE INDEX IF NOT EXISTS idx_project_instances_project_id ON project_instances(project_id);

--
-- MESSAGES TABLE
--
CREATE TABLE IF NOT EXISTS messages
(
    id            TEXT    NOT NULL,
    project_id    TEXT    NOT NULL,
    type          TEXT    NOT NULL,
    title         TEXT    NOT NULL,
    message       TEXT    NOT NULL,
    severity      TEXT    NOT NULL,
    category      TEXT    NOT NULL,
    source        TEXT    NOT NULL,
    created_at_us INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_messages_project_created ON messages(project_id, created_at_us);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at_us);
CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_dedup ON messages(project_id, type, created_at_us);

--
-- ALERTS TABLE
--
CREATE TABLE IF NOT EXISTS alerts
(
    id            TEXT    NOT NULL,
    project_id    TEXT    NOT NULL,
    type          TEXT    NOT NULL,
    title         TEXT    NOT NULL,
    message       TEXT    NOT NULL,
    severity      TEXT    NOT NULL,
    category      TEXT    NOT NULL,
    source        TEXT    NOT NULL,
    created_at_us INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_alerts_project_created ON alerts(project_id, created_at_us);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON alerts(created_at_us);
CREATE UNIQUE INDEX IF NOT EXISTS idx_alerts_dedup ON alerts(project_id, type, created_at_us);
