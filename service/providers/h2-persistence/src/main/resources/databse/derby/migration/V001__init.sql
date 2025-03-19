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
CREATE TABLE IF NOT EXISTS projects
(
    project_id   VARCHAR    NOT NULL,
    project_name VARCHAR    NOT NULL,
    created_at   BIGINT NOT NULL,
    PRIMARY KEY (project_id)
);

CREATE TABLE IF NOT EXISTS schedulers
(
    project_id VARCHAR NOT NULL,
    id         VARCHAR NOT NULL,
    job_type   VARCHAR NOT NULL,
    params     VARCHAR NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE IF NOT EXISTS repositories
(
    project_id VARCHAR NOT NULL,
    id         VARCHAR NOT NULL,
    path       VARCHAR NOT NULL,
    type     VARCHAR NOT NULL,
    PRIMARY KEY (project_id, id)
);

--
-- PROFILE TABLES - COMMON
--
CREATE TABLE IF NOT EXISTS profiles
(
    profile_id            VARCHAR    NOT NULL,
    project_id            VARCHAR    NOT NULL,
    profile_name          VARCHAR    NOT NULL,
    event_source          VARCHAR    NOT NULL,
    event_fields_setting  VARCHAR    NOT NULL,
    created_at            BIGINT NOT NULL,
    profiling_started_at  BIGINT,
    profiling_finished_at BIGINT,
    initialized_at        BIGINT,
    enabled_at            BIGINT,
    PRIMARY KEY (profile_id)
);

CREATE TABLE IF NOT EXISTS saved_graphs
(
    profile_id  VARCHAR    NOT NULL,
    id          VARCHAR    NOT NULL,
    name        VARCHAR    NULL,
    params      BLOB    NOT NULL,
    content     BLOB    NOT NULL,
    created_at  BIGINT NOT NULL,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE IF NOT EXISTS cache
(
    profile_id  VARCHAR NOT NULL,
    key         VARCHAR NOT NULL,
    content     BLOB NOT NULL,
    PRIMARY KEY (profile_id, key)
);

--
-- PROFILE TABLES - EVENTS
--

CREATE TABLE IF NOT EXISTS event_types
(
    profile_id      VARCHAR    NOT NULL,
    name            VARCHAR    NOT NULL,
    label           VARCHAR    NOT NULL,
    type_id         BIGINT,
    description     VARCHAR,
    categories      VARCHAR,
    source          VARCHAR    NOT NULL,
    subtype         VARCHAR,
    samples         BIGINT NOT NULL,
    weight          BIGINT,
    has_stacktrace  BOOLEAN NOT NULL,
    calculated      BOOLEAN NOT NULL,
    extras          VARCHAR,
    settings        VARCHAR,
    columns         VARCHAR,
    PRIMARY KEY (profile_id, name)
);

CREATE TABLE IF NOT EXISTS events
(
    profile_id           VARCHAR    NOT NULL,
    event_id             BIGINT NOT NULL,
    event_type           VARCHAR    NOT NULL,
    timestamp            BIGINT NOT NULL,
    timestamp_from_start BIGINT NOT NULL,
    duration             BIGINT,
    samples              BIGINT NOT NULL,
    weight               BIGINT,
    weight_entity        VARCHAR,
    stacktrace_id        BIGINT,
    thread_id            BIGINT,
    fields               VARCHAR,
    PRIMARY KEY (profile_id, event_id)
);

CREATE INDEX idx_events_event_type_timestamp_from_start ON events(profile_id, event_type, timestamp_from_start);
-- To effectively process calculated events (NativeLeaks - stores address as weight_entity)
CREATE INDEX idx_events_event_type_weight_entity ON events(profile_id, event_type, weight_entity);

CREATE TABLE IF NOT EXISTS event_fields
(
    profile_id  VARCHAR NOT NULL,
    event_id    BIGINT NOT NULL,
    fields      VARCHAR NOT NULL,
    PRIMARY KEY (profile_id, event_id)
);

CREATE TABLE IF NOT EXISTS stacktraces
(
    profile_id    VARCHAR NOT NULL,
    stacktrace_id BIGINT NOT NULL,
    type_id       BIGINT NOT NULL,
    frames        CLOB    NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id)
);

CREATE TABLE IF NOT EXISTS stacktrace_tags
(
    profile_id    VARCHAR NOT NULL,
    stacktrace_id BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id, tag_id)
);

CREATE TABLE IF NOT EXISTS threads
(
    profile_id VARCHAR NOT NULL,
    thread_id  VARCHAR NOT NULL,
    name       VARCHAR NOT NULL,
    os_id      BIGINT NOT NULL,
    java_id    BIGINT,
    is_virtual BOOLEAN NOT NULL,
    PRIMARY KEY (profile_id, thread_id)
);
