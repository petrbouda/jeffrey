/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

--
-- LOCAL CORE DATABASE SCHEMA
-- Contains tables used by the local deployment. Profile event data is stored in per-profile databases.
--

--
-- RECORDING TABLES
-- Used by both project recordings (project_id set) and quick analysis recordings (project_id NULL).
--

CREATE TABLE IF NOT EXISTS recordings
(
    id                    VARCHAR NOT NULL PRIMARY KEY,
    project_id            VARCHAR,
    recording_name        VARCHAR NOT NULL,
    group_id              VARCHAR,
    event_source          VARCHAR NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    recording_started_at  TIMESTAMPTZ,
    recording_finished_at TIMESTAMPTZ,
    -- The bundle this recording came in with, or NULL for a standalone upload or import.
    bundle_id             VARCHAR
);

CREATE TABLE IF NOT EXISTS recording_files
(
    id             VARCHAR NOT NULL PRIMARY KEY,
    project_id     VARCHAR,
    recording_id   VARCHAR NOT NULL,
    filename       VARCHAR NOT NULL,
    supported_type VARCHAR NOT NULL,
    uploaded_at    TIMESTAMPTZ NOT NULL,
    size_in_bytes  BIGINT  NOT NULL
);


--
-- BUNDLES
-- Several files that arrived together (a hub session download). Each initializable item in it is a
-- recording of its own pointing back through recordings.bundle_id; what cannot be initialized (logs,
-- crash files) is a bundle file, download-only. The bundle, not its members, sits in a group.
--

CREATE TABLE IF NOT EXISTS bundles
(
    id          VARCHAR NOT NULL PRIMARY KEY,
    project_id  VARCHAR,
    bundle_name VARCHAR NOT NULL,
    group_id    VARCHAR,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS bundle_files
(
    id             VARCHAR NOT NULL PRIMARY KEY,
    project_id     VARCHAR,
    bundle_id      VARCHAR NOT NULL,
    filename       VARCHAR NOT NULL,
    supported_type VARCHAR NOT NULL,
    uploaded_at    TIMESTAMPTZ NOT NULL,
    size_in_bytes  BIGINT  NOT NULL
);

CREATE TABLE IF NOT EXISTS recording_groups
(
    id         VARCHAR NOT NULL PRIMARY KEY,
    project_id VARCHAR,
    name       VARCHAR NOT NULL,
    created_at TIMESTAMPTZ
);

--
-- RECORDING TAGS
-- Key-value metadata attached to a recording. Tags whose key starts with "origin." are
-- application-managed (set automatically when a recording lands in QA from a project
-- session) and read-only. Other keys are reserved for user-defined tags.
--

CREATE TABLE IF NOT EXISTS recording_tags
(
    recording_id VARCHAR NOT NULL,
    tag_key      VARCHAR NOT NULL,
    tag_value    VARCHAR NOT NULL,
    PRIMARY KEY (recording_id, tag_key)
);

CREATE INDEX IF NOT EXISTS recording_tags_key_value_idx
    ON recording_tags (tag_key, tag_value);

--
-- PROFILE METADATA TABLE
-- Note: Profile event data (events, stacktraces, frames, threads, cache) is stored in per-profile databases.
--

CREATE TABLE IF NOT EXISTS profiles
(
    profile_id            VARCHAR NOT NULL,
    project_id            VARCHAR,
    workspace_id          VARCHAR,
    profile_name          VARCHAR NOT NULL,
    event_source          VARCHAR NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    recording_id          VARCHAR,
    recording_started_at  TIMESTAMPTZ,
    recording_finished_at TIMESTAMPTZ,
    enabled_at            TIMESTAMPTZ,
    modified              BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (profile_id)
);

--
-- IDE TARGETS TABLE
-- The IntelliJ window a profile is linked to, so a jump to source survives a restart.
-- Only the durable half of the link is stored: the port and pid a window had are meaningless
-- once either process has restarted, and discovery re-resolves them from the project id.
--

CREATE TABLE IF NOT EXISTS ide_targets
(
    profile_id   VARCHAR NOT NULL,
    project_id   VARCHAR NOT NULL,
    project_name VARCHAR,
    ide_name     VARCHAR,
    base_path    VARCHAR,
    PRIMARY KEY (profile_id)
);
