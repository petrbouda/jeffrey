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

CREATE TABLE IF NOT EXISTS main.profiles
(
    id             TEXT PRIMARY KEY,
    name           TEXT    NOT NULL UNIQUE,
    created_at     INTEGER NOT NULL,
    started_at     INTEGER NOT NULL,
    recording_path TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS main.active_settings
(
    event TEXT NOT NULL,
    name  TEXT NOT NULL,
    value TEXT,
    UNIQUE (event, name)
);

CREATE TABLE IF NOT EXISTS main.flamegraphs
(
    id              TEXT PRIMARY KEY,
    profile_id      TEXT    NOT NULL,
    event_type      TEXT    NOT NULL,
    graph_type      TEXT    NOT NULL,
    use_thread_mode BOOLEAN NULL,
    use_weight      BOOLEAN NULL,
    complete        BOOLEAN NULL,
    name            TEXT    NULL,
    created_at      INTEGER NOT NULL,
    content         BLOB    NOT NULL,
    CONSTRAINT predefined_constraint UNIQUE (profile_id, event_type, complete)
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
