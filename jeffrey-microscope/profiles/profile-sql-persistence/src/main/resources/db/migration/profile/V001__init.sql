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
-- PER-PROFILE DATABASE SCHEMA
-- Each profile has its own isolated database file.
-- Profile context (workspace, project) is stored in profile_info table.
--

--
-- CACHE TABLE
--
CREATE TABLE IF NOT EXISTS cache
(
    key     VARCHAR NOT NULL PRIMARY KEY,
    content BLOB    NOT NULL
);

--
-- EVENT TYPES TABLE
--
CREATE TABLE IF NOT EXISTS event_types
(
    name            VARCHAR NOT NULL PRIMARY KEY,
    label           VARCHAR NOT NULL,
    type_id         BIGINT,
    description     VARCHAR,
    categories      VARCHAR,
    source          VARCHAR NOT NULL,
    subtype         VARCHAR,
    has_stacktrace  BOOLEAN NOT NULL,
    extras          VARCHAR,
    settings        VARCHAR,
    columns         VARCHAR
);

--
-- FRAMES TABLE
--
CREATE TABLE IF NOT EXISTS frames
(
    frame_hash      BIGINT NOT NULL PRIMARY KEY,
    class_name      VARCHAR,
    method_name     VARCHAR,
    frame_type      VARCHAR,  -- JIT/Interpreted/Native/C++
    line_number     INTEGER,
    bytecode_index  INTEGER
);

--
-- STACKTRACES TABLE
--
CREATE TABLE IF NOT EXISTS stacktraces
(
    stacktrace_hash   BIGINT NOT NULL PRIMARY KEY,  -- Hash of frame_hashes array for deduplication
    type_id           INTEGER NOT NULL,              -- Numerical representation of the stacktrace type
    frame_hashes      BIGINT[],                      -- Array of references to frames table
    tag_ids           INTEGER[]                      -- Array of tags for categorization and filtering
);

--
-- EVENTS TABLE
--
CREATE TABLE IF NOT EXISTS events
(
    event_type      VARCHAR NOT NULL,
    start_timestamp TIMESTAMPTZ NOT NULL,
    -- Milliseconds elapsed since the profiling start (the recording's chunk start time).
    -- Matches the zero point of Java's RelativeTimeRange, so relative time-range filters
    -- and bucketing can run directly on this integer column (sargable, no per-row EPOCH_MS).
    start_timestamp_from_beginning BIGINT,
    duration        BIGINT,
    samples         BIGINT NOT NULL,
    weight          BIGINT,
    weight_entity   VARCHAR,
    stacktrace_hash BIGINT,    -- Reference to stacktraces.stacktrace_hash
    thread_hash     BIGINT,    -- Hash value
    fields          JSON       -- JSON fields for event-specific data
);

-- No ART indexes on events: analytical scans don't use them, they slow down ingest and bloat the
-- database file. Instead, the table is re-clustered after parsing (CTAS ordered by event_type,
-- start_timestamp_from_beginning) so zone maps prune scans by event type and time range.

--
-- THREADS TABLE
--
CREATE TABLE IF NOT EXISTS threads
(
    thread_hash BIGINT   NOT NULL PRIMARY KEY,
    name        VARCHAR  NOT NULL,
    -- virtual threads do not have os_id
    os_id       BIGINT,
    java_id     BIGINT,
    is_virtual  BOOLEAN  NOT NULL
);

--
-- PROFILE INFO TABLE
-- Stores profile context (workspace, project) - always exactly one row per profile database.
-- Populated when the profile database is created.
--
CREATE TABLE IF NOT EXISTS profile_info
(
    profile_id   VARCHAR NOT NULL PRIMARY KEY,
    project_id   VARCHAR,
    workspace_id VARCHAR
);

--
-- ADVISOR PROMPTS TABLE
-- One AI prompt per sample event type: the markdown the model reads, plus `frame_index`, the same
-- call tree in JSON. The index is cached rather than rebuilt because grounding a claim, grading
-- severity and comparing two profiles must all read the numbers the model was actually shown — a
-- rebuild at a different threshold would answer a subtly different question.
--
CREATE TABLE IF NOT EXISTS advisor_prompts
(
    event_type   VARCHAR     NOT NULL PRIMARY KEY,
    label        VARCHAR     NOT NULL,
    samples      BIGINT      NOT NULL,
    markdown     VARCHAR     NOT NULL,
    frame_index  VARCHAR,
    generated_at TIMESTAMPTZ NOT NULL
);

--
-- ADVISOR RECOMMENDATIONS TABLE
-- One advisor result per sample event type. `severity` is computed by Jeffrey from the measured
-- profile, never graded by the model.
--
CREATE TABLE IF NOT EXISTS advisor_recommendations
(
    event_type      VARCHAR     NOT NULL PRIMARY KEY,
    severity        VARCHAR     NOT NULL DEFAULT 'LOW',
    recommendations VARCHAR     NOT NULL,
    source_ref      VARCHAR,
    generated_at    TIMESTAMPTZ NOT NULL
);

--
-- ADVISOR CLAIMS TABLE
-- One row per citation a recommendation rests on, after it has been checked against the measured
-- call tree and the source tree. Stored structurally rather than left inside the markdown because a
-- free-text report cannot be aggregated, a frame can. `grounded` is false when the cited frame does
-- not appear in the profile at all; such a claim is shown to the user, clearly marked, and excluded
-- from severity.
--
CREATE TABLE IF NOT EXISTS advisor_claims
(
    event_type   VARCHAR     NOT NULL,
    title        VARCHAR     NOT NULL,
    cited_frame  VARCHAR     NOT NULL,
    source_path  VARCHAR,
    grounded     BOOLEAN     NOT NULL DEFAULT false,
    source_found BOOLEAN     NOT NULL DEFAULT false,
    self_pct     DOUBLE      NOT NULL DEFAULT 0,
    total_pct    DOUBLE      NOT NULL DEFAULT 0,
    generated_at TIMESTAMPTZ NOT NULL
);

--
-- PIPELINE RUNS TABLE
-- The terminal snapshot of one staged background run — heap-dump initialization, one event type of an
-- Advisor batch, or any future pipeline. Live progress is deliberately NOT here: it lives in memory and
-- dies with the process, because so does the work it describes. What a user needs after the fact is the
-- last completed run, which is what this stores, and it is what re-renders the kept timeline on return.
--
-- `stages` is a JSON array rather than a table of its own because it is written and read as a whole and
-- rendered as a whole; a row per stage would buy queries nobody asks and a join everybody pays for.
-- `scope_id` is '' rather than NULL for pipelines that run once per profile (the heap dump), and the
-- event type for the Advisor, whose batch stores one row per type — so the primary key works without
-- NULL-comparison rules and a batch is simply every row of its pipeline.
--
CREATE TABLE IF NOT EXISTS pipeline_runs
(
    pipeline_id      VARCHAR     NOT NULL,
    scope_id         VARCHAR     NOT NULL DEFAULT '',
    state            VARCHAR     NOT NULL,
    total_elapsed_ms BIGINT      NOT NULL,
    total_steps      INTEGER     NOT NULL,
    completed_steps  INTEGER     NOT NULL,
    error_code       VARCHAR,
    error_message    VARCHAR,
    stages           VARCHAR     NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    completed_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (pipeline_id, scope_id)
);
