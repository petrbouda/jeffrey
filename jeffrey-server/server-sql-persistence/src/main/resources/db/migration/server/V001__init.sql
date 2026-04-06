/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
-- SERVER DATABASE SCHEMA
-- Contains tables used by the server deployment (LIVE workspaces).
-- Profile event data is stored in per-profile databases.
--

--
-- WORKSPACE TABLES
--

CREATE TABLE IF NOT EXISTS workspaces
(
    workspace_id        VARCHAR PRIMARY KEY,
    workspace_origin_id VARCHAR,
    repository_id       VARCHAR,
    name                VARCHAR NOT NULL,
    description         VARCHAR,
    location            VARCHAR,
    base_location       VARCHAR,
    created_at          TIMESTAMPTZ NOT NULL
);

--
-- PROJECT TABLES
--

CREATE TABLE IF NOT EXISTS projects
(
    project_id              VARCHAR NOT NULL,
    origin_project_id       VARCHAR,
    project_name            VARCHAR NOT NULL,
    project_label           VARCHAR,
    namespace               VARCHAR,
    workspace_id            VARCHAR NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    origin_created_at       TIMESTAMPTZ,
    attributes              VARCHAR NOT NULL,
    graph_visualization     VARCHAR NOT NULL,
    deleted_at              TIMESTAMPTZ,
    PRIMARY KEY (project_id)
);

CREATE INDEX IF NOT EXISTS idx_projects_workspace_id ON projects(workspace_id);
CREATE INDEX IF NOT EXISTS idx_projects_namespace ON projects(namespace);

CREATE TABLE IF NOT EXISTS schedulers
(
    id         VARCHAR NOT NULL,
    project_id VARCHAR,
    job_type   VARCHAR NOT NULL,
    params     VARCHAR NOT NULL,
    enabled    BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

--
-- REPOSITORY TABLES
--

CREATE TABLE IF NOT EXISTS repositories
(
    project_id               VARCHAR NOT NULL,
    repository_id            VARCHAR NOT NULL,
    repository_type          VARCHAR NOT NULL,
    workspaces_path          VARCHAR,
    relative_workspace_path  VARCHAR NOT NULL,
    relative_project_path    VARCHAR NOT NULL,
    PRIMARY KEY (project_id, repository_id)
);

CREATE TABLE IF NOT EXISTS project_instance_sessions
(
    session_id            VARCHAR NOT NULL,
    repository_id         VARCHAR NOT NULL,
    instance_id           VARCHAR NOT NULL,
    session_order         INTEGER NOT NULL DEFAULT 1,
    relative_session_path VARCHAR NOT NULL,
    profiler_settings     VARCHAR,
    origin_created_at     TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    finished_at           TIMESTAMPTZ,
    PRIMARY KEY (repository_id, session_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_instance_sessions_session_path ON project_instance_sessions(repository_id, relative_session_path);
CREATE INDEX IF NOT EXISTS idx_project_instance_sessions_instance_id ON project_instance_sessions(instance_id);

--
-- PROJECT INSTANCE TABLES
--

CREATE TABLE IF NOT EXISTS project_instances
(
    instance_id    VARCHAR NOT NULL,
    project_id     VARCHAR NOT NULL,
    hostname       VARCHAR NOT NULL,
    status         VARCHAR NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMPTZ NOT NULL,
    finished_at    TIMESTAMPTZ,
    expiring_at    TIMESTAMPTZ,
    expired_at     TIMESTAMPTZ,
    PRIMARY KEY (instance_id)
);

CREATE INDEX IF NOT EXISTS idx_project_instances_project_id ON project_instances(project_id);

--
-- PROFILER SETTINGS TABLE
--

CREATE TABLE IF NOT EXISTS profiler_settings
(
    workspace_id    VARCHAR,
    project_id      VARCHAR,
    agent_settings  VARCHAR NOT NULL,
    UNIQUE (workspace_id, project_id)
);

--
-- PERSISTENT QUEUE TABLES
--

CREATE SEQUENCE IF NOT EXISTS persistent_queue_seq START 1;

CREATE TABLE IF NOT EXISTS persistent_queue_events
(
    offset_id  BIGINT DEFAULT nextval('persistent_queue_seq') PRIMARY KEY,
    queue_name VARCHAR NOT NULL,
    scope_id   VARCHAR NOT NULL,
    dedup_key  VARCHAR,
    payload    VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_persistent_queue_events_scope ON persistent_queue_events(queue_name, scope_id, offset_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_persistent_queue_events_dedup ON persistent_queue_events(queue_name, scope_id, dedup_key);
CREATE INDEX IF NOT EXISTS idx_persistent_queue_events_created_at ON persistent_queue_events(created_at);

CREATE TABLE IF NOT EXISTS persistent_queue_consumers
(
    consumer_id       VARCHAR NOT NULL,
    queue_name        VARCHAR NOT NULL,
    scope_id          VARCHAR NOT NULL,
    last_offset       BIGINT DEFAULT 0,
    last_execution_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_id, queue_name, scope_id)
);

