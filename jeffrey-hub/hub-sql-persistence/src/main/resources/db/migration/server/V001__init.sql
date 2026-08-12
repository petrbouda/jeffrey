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
    workspace_id  VARCHAR PRIMARY KEY,
    reference_id  VARCHAR NOT NULL,
    repository_id VARCHAR,
    name          VARCHAR NOT NULL,
    location      VARCHAR,
    base_location VARCHAR,
    created_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (reference_id),
    UNIQUE (name)
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
-- Supports findByOriginProjectId and the synchronizer's duplicate-origin guard.
-- Not UNIQUE: a soft-deleted project may legitimately coexist with its re-created successor,
-- and DuckDB has no partial indexes to scope uniqueness to deleted_at IS NULL rows.
CREATE INDEX IF NOT EXISTS idx_projects_origin_project_id ON projects(origin_project_id);

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

-- The event-streaming hot path joins repositories by repository_id alone, which is not the
-- leftmost column of the primary key and would otherwise require a full scan per lookup.
CREATE INDEX IF NOT EXISTS idx_repositories_repository_id ON repositories(repository_id);

CREATE TABLE IF NOT EXISTS project_instance_sessions
(
    session_id            VARCHAR NOT NULL,
    repository_id         VARCHAR NOT NULL,
    instance_id           VARCHAR NOT NULL,
    session_order         INTEGER NOT NULL DEFAULT 1,
    relative_session_path VARCHAR NOT NULL,
    origin_created_at     TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    finished_at           TIMESTAMPTZ,
    -- Retained sessions are exempt from every retention job (age-based and quota-based).
    -- Set manually via the repository API, or automatically when a JVM crash log is detected.
    retained              BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (repository_id, session_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_instance_sessions_session_path ON project_instance_sessions(repository_id, relative_session_path);
CREATE INDEX IF NOT EXISTS idx_project_instance_sessions_instance_id ON project_instance_sessions(instance_id);
-- The event-streaming hot path looks sessions up by session_id alone, which is not the
-- leftmost column of the primary key and would otherwise require a full scan per lookup.
CREATE INDEX IF NOT EXISTS idx_project_instance_sessions_session_id ON project_instance_sessions(session_id);

--
-- PROJECT INSTANCE TABLES
--

CREATE TABLE IF NOT EXISTS project_instances
(
    instance_id    VARCHAR NOT NULL,
    project_id     VARCHAR NOT NULL,
    instance_name  VARCHAR NOT NULL,
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
