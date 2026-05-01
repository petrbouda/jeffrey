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
    duration        BIGINT,
    samples         BIGINT NOT NULL,
    weight          BIGINT,
    weight_entity   VARCHAR,
    stacktrace_hash BIGINT,    -- Reference to stacktraces.stacktrace_hash
    thread_hash     BIGINT,    -- Hash value
    fields          JSON       -- JSON fields for event-specific data
);

-- Optimized indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_events_composite ON events(event_type, start_timestamp, stacktrace_hash);
-- To effectively process calculated events (NativeLeaks - stores address as weight_entity)
CREATE INDEX IF NOT EXISTS idx_events_event_type_weight_entity ON events(event_type, weight_entity);

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
