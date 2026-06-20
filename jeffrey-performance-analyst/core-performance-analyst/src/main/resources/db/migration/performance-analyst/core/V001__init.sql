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
-- PERFORMANCE ANALYST CORE DATABASE SCHEMA (SQLite)
-- The single store for the performance-analyst deployment: connected jeffrey-hub registry,
-- downloaded recordings (files/groups/tags), local projects, their AI configuration,
-- project<->recording membership, and the AI flamegraph prompts generated per recording.
-- Timestamps are stored as INTEGER epoch milliseconds (SQLite has no native timestamp type);
-- booleans are stored as INTEGER 0/1.
--

--
-- PROJECTS
-- A user-created local project that groups recordings and holds analysis configuration.
--
CREATE TABLE IF NOT EXISTS projects
(
    id           TEXT NOT NULL PRIMARY KEY,
    name         TEXT NOT NULL,
    description  TEXT,
    created_at   INTEGER NOT NULL,
    modified_at  INTEGER NOT NULL
);

--
-- PROJECT AI CONFIGURATION
-- One AI configuration per project (provider/model and the flamegraph prune threshold).
--
CREATE TABLE IF NOT EXISTS project_ai_configuration
(
    project_id           TEXT NOT NULL PRIMARY KEY,
    provider             TEXT,
    model                TEXT,
    prune_threshold_pct  REAL NOT NULL DEFAULT 1.0,
    modified_at          INTEGER NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

--
-- PROJECT RECORDINGS (membership)
-- Links a project to the recordings it groups. recording_id is a soft reference to the
-- recording stored in the recordings table below.
--
CREATE TABLE IF NOT EXISTS project_recordings
(
    project_id    TEXT NOT NULL,
    recording_id  TEXT NOT NULL,
    added_at      INTEGER NOT NULL,
    PRIMARY KEY (project_id, recording_id),
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

--
-- GENERATED PROMPTS
-- One AI flamegraph prompt per (recording, sample event type). project_id is optional until the
-- recording is assigned to a project. Re-generation upserts on the (recording_id, event_type) key.
--
CREATE TABLE IF NOT EXISTS generated_prompts
(
    recording_id  TEXT NOT NULL,
    event_type    TEXT NOT NULL,
    project_id    TEXT,
    label         TEXT NOT NULL,
    samples       INTEGER NOT NULL,
    markdown      TEXT NOT NULL,
    generated_at  INTEGER NOT NULL,
    PRIMARY KEY (recording_id, event_type)
);

CREATE INDEX IF NOT EXISTS idx_generated_prompts_recording ON generated_prompts (recording_id);

--
-- HUBS (remote jeffrey-hub registry)
-- Connected jeffrey-hub servers browsed in WorkspacesBrowser. plaintext: gRPC client uses
-- cleartext h2c when 1, TLS when 0.
--
CREATE TABLE IF NOT EXISTS hubs
(
    hub_id      TEXT NOT NULL PRIMARY KEY,
    name        TEXT NOT NULL,
    hostname    TEXT NOT NULL,
    port        INTEGER NOT NULL DEFAULT 443,
    plaintext   INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL,
    UNIQUE (hostname, port)
);

--
-- RECORDINGS
-- Recordings downloaded from a remote hub. project_id stays NULL (the analyst uses the
-- unscoped recording store; project membership is tracked separately in project_recordings).
--
CREATE TABLE IF NOT EXISTS recordings
(
    id                    TEXT NOT NULL PRIMARY KEY,
    project_id            TEXT,
    recording_name        TEXT NOT NULL,
    group_id              TEXT,
    event_source          TEXT NOT NULL,
    created_at            INTEGER NOT NULL,
    recording_started_at  INTEGER,
    recording_finished_at INTEGER
);

CREATE TABLE IF NOT EXISTS recording_files
(
    id             TEXT NOT NULL PRIMARY KEY,
    project_id     TEXT,
    recording_id   TEXT NOT NULL,
    filename       TEXT NOT NULL,
    supported_type TEXT NOT NULL,
    uploaded_at    INTEGER NOT NULL,
    size_in_bytes  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS recording_groups
(
    id         TEXT NOT NULL PRIMARY KEY,
    project_id TEXT,
    name       TEXT NOT NULL,
    created_at INTEGER
);

--
-- RECORDING TAGS
-- Key-value metadata attached to a recording.
--
CREATE TABLE IF NOT EXISTS recording_tags
(
    recording_id TEXT NOT NULL,
    tag_key      TEXT NOT NULL,
    tag_value    TEXT NOT NULL,
    PRIMARY KEY (recording_id, tag_key)
);

CREATE INDEX IF NOT EXISTS recording_tags_key_value_idx
    ON recording_tags (tag_key, tag_value);
