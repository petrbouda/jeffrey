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

CREATE TABLE IF NOT EXISTS main.event_types
(
    name            TEXT PRIMARY KEY,
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
    columns         TEXT
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS main.event_type_settings
(
    event_name  TEXT NOT NULL,
    name        TEXT NOT NULL,
    value       TEXT NOT NULL,
    PRIMARY KEY (event_name, name)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS main.events
(
    event_id             INTEGER PRIMARY KEY,
    event_name           TEXT    NOT NULL,
    timestamp            INTEGER NOT NULL,
    timestamp_from_start INTEGER NOT NULL,
    duration             INTEGER,
    samples              INTEGER NOT NULL,
    weight               INTEGER,
    weight_entity        TEXT,
    stacktrace_id        INTEGER,
    thread_id            INTEGER,
    fields               TEXT
);

CREATE INDEX idx_events_event_name_timestamp_from_start ON events(event_name, timestamp_from_start);

CREATE TABLE IF NOT EXISTS main.stacktraces
(
    stacktrace_id INTEGER PRIMARY KEY,
    type_id       INTEGER NOT NULL,
    frames        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS main.stacktrace_tags
(
    stacktrace_id INTEGER NOT NULL,
    tag_id        INTEGER NOT NULL,
    PRIMARY KEY (stacktrace_id, tag_id)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS main.threads
(
    thread_id  INTEGER PRIMARY KEY,
    name       TEXT NOT NULL,
    os_id      INTEGER NOT NULL,
    java_id    INTEGER,
    is_virtual BOOLEAN NOT NULL
);
