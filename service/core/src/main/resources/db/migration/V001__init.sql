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
CREATE TABLE IF NOT EXISTS main.projects
(
    project_id   TEXT    NOT NULL,
    project_name TEXT    NOT NULL,
    created_at   INTEGER NOT NULL,
    PRIMARY KEY (project_id)
);

CREATE TABLE IF NOT EXISTS main.kv_store
(
    project_id TEXT NOT NULL,
    key        TEXT NOT NULL,
    content    BLOB NOT NULL,
    PRIMARY KEY (project_id, key)
);

CREATE TABLE IF NOT EXISTS main.scheduler
(
    project_id TEXT NOT NULL,
    id         TEXT NOT NULL,
    job_type   TEXT NOT NULL,
    params     TEXT NOT NULL,
    PRIMARY KEY (project_id, id)
);

--
-- PROFILE TABLES - COMMON
--
CREATE TABLE IF NOT EXISTS main.profiles
(
    profile_id            TEXT    NOT NULL,
    project_id            TEXT    NOT NULL,
    profile_name          TEXT    NOT NULL,
    created_at            INTEGER NOT NULL,
    profiling_started_at  INTEGER,
    profiling_finished_at INTEGER,
    initialized_at        INTEGER,
    enabled_at            INTEGER,
    PRIMARY KEY (profile_id)
);

CREATE TABLE IF NOT EXISTS main.flamegraphs
(
    profile_id      TEXT    NOT NULL,
    id              TEXT    NOT NULL,
    event_type      TEXT    NOT NULL,
    graph_type      TEXT    NOT NULL,
    use_thread_mode BOOLEAN NULL,
    use_weight      BOOLEAN NULL,
    name            TEXT    NULL,
    created_at      INTEGER NOT NULL,
    content         BLOB    NOT NULL,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE IF NOT EXISTS main.cache
(
    profile_id  TEXT NOT NULL,
    key         TEXT NOT NULL,
    content     BLOB NOT NULL,
    PRIMARY KEY (profile_id, key)
);

--
-- PROFILE TABLES - EVENTS
--

CREATE TABLE IF NOT EXISTS main.event_types
(
    profile_id      TEXT    NOT NULL,
    name            TEXT    NOT NULL,
    label           TEXT    NOT NULL,
    type_id         INTEGER,
    description     TEXT,
    categories      TEXT,
    source          TEXT    NOT NULL,
    subtype         TEXT,
    samples         INTEGER NOT NULL,
    weight          INTEGER,
    has_stacktrace  BOOLEAN NOT NULL,
    calculated      BOOLEAN NOT NULL,
    extras          TEXT,
    settings        TEXT,
    columns         TEXT,
    PRIMARY KEY (profile_id, name)
);

CREATE TABLE IF NOT EXISTS main.events
(
    profile_id           TEXT    NOT NULL,
    event_id             INTEGER NOT NULL,
    event_name           TEXT    NOT NULL,
    timestamp            INTEGER NOT NULL,
    timestamp_from_start INTEGER NOT NULL,
    duration             INTEGER,
    samples              INTEGER NOT NULL,
    weight               INTEGER,
    weight_entity        TEXT,
    stacktrace_id        INTEGER,
    thread_id            INTEGER,
    fields               TEXT,
    PRIMARY KEY (profile_id, event_id)
);

CREATE INDEX idx_events_event_name_timestamp_from_start ON events(profile_id, event_name, timestamp_from_start);
-- To effectively process calculated events (NativeLeaks - stores address as weight_entity)
CREATE INDEX idx_events_event_name_weight_entity ON events(profile_id, event_name, weight_entity);

CREATE TABLE IF NOT EXISTS main.stacktraces
(
    profile_id    TEXT    NOT NULL,
    stacktrace_id INTEGER NOT NULL,
    type_id       INTEGER NOT NULL,
    frames        TEXT    NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id)
);

CREATE TABLE IF NOT EXISTS main.stacktrace_tags
(
    profile_id    TEXT    NOT NULL,
    stacktrace_id INTEGER NOT NULL,
    tag_id        INTEGER NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id, tag_id)
);

CREATE TABLE IF NOT EXISTS main.threads
(
    profile_id TEXT    NOT NULL,
    thread_id  TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    os_id      INTEGER NOT NULL,
    java_id    INTEGER,
    is_virtual BOOLEAN NOT NULL,
    PRIMARY KEY (profile_id, thread_id)
);
