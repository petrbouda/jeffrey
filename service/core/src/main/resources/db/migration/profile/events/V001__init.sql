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
    event_name    TEXT    NOT NULL,
    timestamp     INTEGER NOT NULL,
    duration      INTEGER,
    samples       INTEGER NOT NULL,
    weight        INTEGER,
    stacktrace_id TEXT,
    thread_id     TEXT,
    fields        TEXT
);

CREATE TABLE IF NOT EXISTS main.stacktraces
(
    stacktrace_id TEXT PRIMARY KEY,
    type          TEXT,
    subtype       TEXT,
    frames        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS main.threads
(
    thread_id  TEXT PRIMARY KEY,
    os_id      TEXT,
    os_name    TEXT,
    java_id    TEXT,
    java_name  TEXT,
    is_virtual BOOLEAN
);
