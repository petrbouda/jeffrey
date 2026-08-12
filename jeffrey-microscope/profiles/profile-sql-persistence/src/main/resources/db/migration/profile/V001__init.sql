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
-- One AI prompt per sample event type. `prompt` is the complete user message as the model receives
-- it, composed when the prompt is generated rather than at run time, so the run sends it verbatim and
-- the Advisor's Prompt page shows exactly what was asked.
--
CREATE TABLE IF NOT EXISTS advisor_prompts
(
    event_type        VARCHAR     NOT NULL PRIMARY KEY,
    label             VARCHAR     NOT NULL,
    samples           BIGINT      NOT NULL,
    prompt            VARCHAR     NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL
);

--
-- ADVISOR RECOMMENDATIONS TABLE
-- One advisor result per sample event type. `recommendation` is the markdown the model wrote — the
-- column repeats the table name because the artifact has exactly one name everywhere else too, and a
-- column unique to this table would be the odd one out. `patch` is the unified diff the model
-- proposed, already repaired by UnifiedDiffNormalizer so `git apply` accepts it; NULL when it
-- proposed no code edit — a patch is attempted for every recommendation, so NULL means the model
-- had no concrete edit to offer rather than that the type was skipped.
--
CREATE TABLE IF NOT EXISTS advisor_recommendations
(
    event_type        VARCHAR     NOT NULL PRIMARY KEY,
    recommendation    VARCHAR     NOT NULL,
    patch             VARCHAR,
    source_ref        VARCHAR,
    generated_at      TIMESTAMPTZ NOT NULL
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

--
-- TRACE SPANS TABLE
-- Spans arrive in `events` like any other JFR event, with their identity in the JSON `fields`.
-- Deriving them into typed columns once, after parsing, keeps every trace query off JSON
-- extraction: assembling a tree, listing the slowest traces and aggregating per operation all
-- run against BIGINTs instead of re-parsing `fields` per row, per query.
--
-- Every traced event type feeds this one table, which is what makes an HTTP request show its JDBC
-- statements as native children: they are rows here just like a hand-written span is.
--
-- `parent_span_id` is NULL for a root span (the wire encoding uses 0 for "absent", normalised here
-- so SQL null-semantics apply). `attributes` keeps the source event's whole `fields` object, so a
-- span carries everything its originating event knew without this table having to model it.
--
CREATE TABLE IF NOT EXISTS trace_spans
(
    trace_id                       BIGINT      NOT NULL,
    span_id                        BIGINT      NOT NULL,
    parent_span_id                 BIGINT,
    name                           VARCHAR     NOT NULL,
    kind                           VARCHAR,
    status                         VARCHAR,
    error_type                     VARCHAR,
    attributes                     JSON,
    start_timestamp                TIMESTAMPTZ NOT NULL,
    -- Same zero point as events.start_timestamp_from_beginning, so a trace can be lined up
    -- against the recording's other views without converting.
    start_timestamp_from_beginning BIGINT      NOT NULL,
    duration                       BIGINT      NOT NULL,
    thread_hash                    BIGINT,
    -- Which event produced this span: jeffrey.TraceSpan, jeffrey.HttpServerExchange, ...
    event_type                     VARCHAR     NOT NULL
);

--
-- TRACES TABLE
-- One row per trace, so the trace list is a single scan of a small table rather than an
-- aggregation over every span. Derived from trace_spans immediately after it is filled.
--
-- The root is the earliest span without a parent; a trace whose real root went unrecorded (below
-- the event threshold, say) still gets one, because the ordering falls back to the earliest span.
-- Duration spans the whole trace rather than the root's own duration, which is the same number
-- whenever the root encloses its children and the more honest one when it does not.
--
CREATE TABLE IF NOT EXISTS traces
(
    trace_id                       BIGINT      NOT NULL PRIMARY KEY,
    root_name                      VARCHAR,
    root_kind                      VARCHAR,
    start_timestamp                TIMESTAMPTZ NOT NULL,
    start_timestamp_from_beginning BIGINT      NOT NULL,
    duration                       BIGINT      NOT NULL,
    span_count                     INTEGER     NOT NULL,
    error_count                    INTEGER     NOT NULL
);
