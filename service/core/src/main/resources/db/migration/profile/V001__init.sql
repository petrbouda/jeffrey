/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
-- pragma journal_mode=wal;
pragma synchronous=off;
pragma journal_mode=wal;

CREATE TABLE IF NOT EXISTS main.profile
(
    id             TEXT PRIMARY KEY,
    name           TEXT    NOT NULL,
    project_id     TEXT    NOT NULL,
    created_at     INTEGER NOT NULL,
    started_at     INTEGER NOT NULL,
    finished_at    INTEGER
);

CREATE TABLE IF NOT EXISTS main.event_types
(
    name        TEXT PRIMARY KEY,
    label       TEXT    NOT NULL,
    description TEXT,
    categories  TEXT    NOT NULL,
    source      TEXT    NOT NULL,
    subtype     TEXT,
    samples     INTEGER NOT NULL,
    weight      INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS main.events
(
    event_id      TEXT PRIMARY KEY,
    event_name    TEXT    NOT NULL,
    timestamp     INTEGER NOT NULL,
    duration      INTEGER,
    samples       INTEGER NOT NULL,
    weight        INTEGER,
    stacktrace_id TEXT,
    fields        TEXT
);

CREATE TABLE IF NOT EXISTS main.stacktraces
(
    stacktrace_id TEXT PRIMARY KEY,
    thread_id     TEXT NOT NULL,
    type          TEXT,
    subtype       TEXT,
    frames        TEXT
);

CREATE TABLE IF NOT EXISTS main.threads
(
    thread_id  TEXT PRIMARY KEY,
    os_id      TEXT,
    java_id    TEXT,
    os_name    TEXT,
    java_name  TEXT,
    is_virtual BOOLEAN
);

CREATE TABLE IF NOT EXISTS main.flamegraphs
(
    id              TEXT PRIMARY KEY,
    profile_id      TEXT    NOT NULL,
    event_type      TEXT    NOT NULL,
    graph_type      TEXT    NOT NULL,
    use_thread_mode BOOLEAN NULL,
    use_weight      BOOLEAN NULL,
    name            TEXT    NULL,
    created_at      INTEGER NOT NULL,
    content         BLOB    NOT NULL
);

CREATE TABLE IF NOT EXISTS main.cache
(
    key     TEXT PRIMARY KEY,
    content BLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS main.heatmaps
(
    id         TEXT PRIMARY KEY,
    profile_id TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    created_at INTEGER NOT NULL,
    content    BLOB    NOT NULL,
    CONSTRAINT heatmap_type UNIQUE (profile_id, name)
);

CREATE TABLE IF NOT EXISTS main.timeseries
(
    id         TEXT PRIMARY KEY,
    profile_id TEXT    NOT NULL,
    event_type TEXT    NOT NULL,
    created_at INTEGER NOT NULL,
    content    BLOB    NOT NULL,
    CONSTRAINT timeseries_type UNIQUE (profile_id, event_type)
);
