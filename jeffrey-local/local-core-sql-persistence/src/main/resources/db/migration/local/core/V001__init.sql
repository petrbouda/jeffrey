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
-- LOCAL CORE DATABASE SCHEMA
-- Contains tables used by the local deployment. Profile event data is stored in per-profile databases.
--

--
-- WORKSPACE TABLES
--

CREATE TABLE IF NOT EXISTS workspaces
(
    workspace_id        VARCHAR PRIMARY KEY,
    workspace_origin_id VARCHAR NOT NULL,
    base_location       VARCHAR NOT NULL,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE
);

--
-- RECORDING TABLES
--

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
-- PROFILER SETTINGS TABLE
--

CREATE TABLE IF NOT EXISTS profiler_settings
(
    workspace_id    VARCHAR,
    project_id      VARCHAR,
    agent_settings  VARCHAR NOT NULL,
    UNIQUE (workspace_id, project_id)
);

--
-- QUICK PROFILES TABLE
--

CREATE TABLE IF NOT EXISTS quick_profiles
(
    profile_id            VARCHAR NOT NULL,
    profile_name          VARCHAR NOT NULL,
    group_name            VARCHAR,
    event_source          VARCHAR NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    profiling_started_at  TIMESTAMPTZ NOT NULL,
    profiling_finished_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (profile_id)
);

CREATE INDEX IF NOT EXISTS idx_quick_profiles_group ON quick_profiles(group_name);
