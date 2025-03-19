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
CREATE TABLE projects
(
    project_id   VARCHAR(32672) NOT NULL,
    project_name VARCHAR(32672) NOT NULL,
    created_at   BIGINT NOT NULL,
    PRIMARY KEY (project_id)
);

CREATE TABLE schedulers
(
    project_id VARCHAR(32672) NOT NULL,
    id         VARCHAR(32672) NOT NULL,
    job_type   VARCHAR(32672) NOT NULL,
    params     VARCHAR(32672) NOT NULL,
    PRIMARY KEY (project_id, id)
);

CREATE TABLE repositories
(
    project_id VARCHAR(32672) NOT NULL,
    id         VARCHAR(32672) NOT NULL,
    path       VARCHAR(32672) NOT NULL,
    type     VARCHAR(32672) NOT NULL,
    PRIMARY KEY (project_id, id)
);

--
-- PROFILE TABLES - COMMON
--
CREATE TABLE profiles
(
    profile_id            VARCHAR(32672)    NOT NULL,
    project_id            VARCHAR(32672)    NOT NULL,
    profile_name          VARCHAR(32672)    NOT NULL,
    event_source          VARCHAR(32672)    NOT NULL,
    event_fields_setting  VARCHAR(32672)    NOT NULL,
    created_at            BIGINT NOT NULL,
    profiling_started_at  BIGINT,
    profiling_finished_at BIGINT,
    initialized_at        BIGINT,
    enabled_at            BIGINT,
    PRIMARY KEY (profile_id)
);

CREATE TABLE saved_graphs
(
    profile_id  VARCHAR(32672)    NOT NULL,
    id          VARCHAR(32672)    NOT NULL,
    name        VARCHAR(32672)    NOT NULL,
    params      BLOB    NOT NULL,
    content     BLOB    NOT NULL,
    created_at  BIGINT NOT NULL,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE cache
(
    profile_id  VARCHAR(32672) NOT NULL,
    key_name        VARCHAR(32672) NOT NULL,
    content     BLOB NOT NULL,
    PRIMARY KEY (profile_id, key_name)
);

--
-- PROFILE TABLES - EVENTS
--

CREATE TABLE event_types
(
    profile_id      VARCHAR(32672)    NOT NULL,
    name            VARCHAR(32672)    NOT NULL,
    label           VARCHAR(32672)    NOT NULL,
    type_id         BIGINT,
    description     VARCHAR(32672),
    categories      VARCHAR(32672),
    source          VARCHAR(32672)    NOT NULL,
    subtype         VARCHAR(32672),
    samples         BIGINT NOT NULL,
    weight          BIGINT,
    has_stacktrace  BOOLEAN NOT NULL,
    calculated      BOOLEAN NOT NULL,
    extras          VARCHAR(32672),
    settings        VARCHAR(32672),
    columns         VARCHAR(32672),
    PRIMARY KEY (profile_id, name)
);

CREATE TABLE events
(
    profile_id           VARCHAR(32672)    NOT NULL,
    event_id             BIGINT NOT NULL,
    event_type           VARCHAR(32672)    NOT NULL,
    timestamp            BIGINT NOT NULL,
    timestamp_from_start BIGINT NOT NULL,
    duration             BIGINT,
    samples              BIGINT NOT NULL,
    weight               BIGINT,
    weight_entity        VARCHAR(32672),
    stacktrace_id        BIGINT,
    thread_id            BIGINT,
    fields               VARCHAR(32672),
    PRIMARY KEY (profile_id, event_id)
);

CREATE INDEX idx_events_event_type_timestamp_from_start ON events(profile_id, event_type, timestamp_from_start);
-- To effectively process calculated events (NativeLeaks - stores address as weight_entity)
CREATE INDEX idx_events_event_type_weight_entity ON events(profile_id, event_type, weight_entity);

CREATE TABLE event_fields
(
    profile_id  VARCHAR(32672) NOT NULL,
    event_id    BIGINT NOT NULL,
    fields      VARCHAR(32672) NOT NULL,
    PRIMARY KEY (profile_id, event_id)
);

CREATE TABLE stacktraces
(
    profile_id    VARCHAR(32672) NOT NULL,
    stacktrace_id BIGINT NOT NULL,
    type_id       BIGINT NOT NULL,
    frames        CLOB    NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id)
);

CREATE TABLE stacktrace_tags
(
    profile_id    VARCHAR(32672) NOT NULL,
    stacktrace_id BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,
    PRIMARY KEY (profile_id, stacktrace_id, tag_id)
);

CREATE TABLE threads
(
    profile_id VARCHAR(32672) NOT NULL,
    thread_id  VARCHAR(32672) NOT NULL,
    name       VARCHAR(32672) NOT NULL,
    os_id      BIGINT NOT NULL,
    java_id    BIGINT,
    is_virtual BOOLEAN NOT NULL,
    PRIMARY KEY (profile_id, thread_id)
);
