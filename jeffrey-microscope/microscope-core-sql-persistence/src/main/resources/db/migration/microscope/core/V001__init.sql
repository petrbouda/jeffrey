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
-- HUB TABLES
-- One row per connected jeffrey-hub. Workspaces are NOT stored locally —
-- they are listed live from the server via gRPC ListWorkspaces.
--

CREATE TABLE IF NOT EXISTS hubs
(
    hub_id   VARCHAR PRIMARY KEY,
    name        VARCHAR NOT NULL,
    hostname    VARCHAR NOT NULL,
    port        INTEGER NOT NULL DEFAULT 443,
    -- gRPC client uses cleartext h2c when true, TLS when false. Default false
    -- preserves the existing public-internet TLS workflow; flip to true for
    -- in-cluster Service DNS or trusted-LAN setups.
    plaintext   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (hostname, port)
);

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
    recording_finished_at TIMESTAMPTZ
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
-- APPLICATION SETTINGS TABLE
-- Stores user-configurable application settings as key-value pairs grouped by category.
-- Secret values (e.g., API keys) are stored encrypted with machine-bound AES-256-GCM.
--

CREATE TABLE IF NOT EXISTS settings
(
    category VARCHAR NOT NULL,
    name     VARCHAR NOT NULL,
    value    VARCHAR NOT NULL,
    secret   BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (category, name)
);

--
-- GUARDIANS
-- Central, editable definitions of every Guardian guard. Built-in guards (built_in = true) are
-- seeded below; users can edit them or add custom guards from the Microscope UI. event_type is the
-- free-form JFR event type (e.g. jdk.ExecutionSample) whose stacktraces the guard analyses — any
-- event type carrying stacktraces is allowed. matcher_spec and preconditions hold JSON (stored as
-- text; parsed in Java) — see the MatchExpr / TraversalStrategy sealed types in the profile-guardian
-- module.
--

CREATE TABLE IF NOT EXISTS guardians
(
    guard_id          VARCHAR     NOT NULL PRIMARY KEY,
    name              VARCHAR     NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT true,
    built_in          BOOLEAN     NOT NULL DEFAULT false,
    event_type        VARCHAR     NOT NULL,
    category          VARCHAR     NOT NULL,
    result_type       VARCHAR     NOT NULL,
    target_frame      VARCHAR     NOT NULL,
    matching_type     VARCHAR     NOT NULL,
    info_threshold    DOUBLE      NOT NULL,
    warning_threshold DOUBLE      NOT NULL,
    min_samples       BIGINT      NOT NULL DEFAULT 1000,
    matcher_spec      VARCHAR     NOT NULL,
    preconditions     VARCHAR,
    summary_noun      VARCHAR,
    explanation       VARCHAR,
    solution          VARCHAR,
    created_at        TIMESTAMPTZ NOT NULL
);

--
-- PROJECT ADVISOR SETTINGS TABLE
-- Per-project configuration for the profile Advisor, keyed the same way profiler_settings is:
-- workspaces and projects are listed live from the hub over gRPC, so there is no local projects table
-- to reference and the identity has to be carried as a plain pair.
--
-- source_path points at the working copy on THIS machine, which is why the setting is local even
-- though the project is remote. compile_command and test_command are blank by default: the patch
-- verification ladder reports those levels as skipped rather than running a build nobody asked for.
--
CREATE TABLE IF NOT EXISTS project_advisor_settings
(
    workspace_id            VARCHAR,
    project_id              VARCHAR,
    source_path             VARCHAR,
    prune_threshold_pct     DOUBLE      NOT NULL DEFAULT 1.0,
    regression_threshold_pp DOUBLE      NOT NULL DEFAULT 2.0,
    compile_command         VARCHAR,
    test_command            VARCHAR,
    modified_at             TIMESTAMPTZ NOT NULL,
    UNIQUE (workspace_id, project_id)
);

--
-- ADVISOR CLAIM INDEX TABLE
-- Grounded claims duplicated out of the per-profile databases so the fleet rollup can ask a question
-- no single profile can answer: which frames are hot across more than one project. Per-profile
-- databases are isolated by design, so a cross-profile query needs a shared table rather than a fan-out
-- over every profile file.
--
-- Only grounded claims land here. An unverified citation is worth showing on the profile that produced
-- it, but rolling it up would let one invented frame masquerade as a fleet-wide pattern.
--
CREATE TABLE IF NOT EXISTS advisor_claim_index
(
    profile_id   VARCHAR     NOT NULL,
    profile_name VARCHAR,
    workspace_id VARCHAR,
    project_id   VARCHAR     NOT NULL,
    event_type   VARCHAR     NOT NULL,
    title        VARCHAR     NOT NULL,
    cited_frame  VARCHAR     NOT NULL,
    source_path  VARCHAR,
    self_pct     DOUBLE      NOT NULL DEFAULT 0,
    total_pct    DOUBLE      NOT NULL DEFAULT 0,
    generated_at TIMESTAMPTZ NOT NULL
);
