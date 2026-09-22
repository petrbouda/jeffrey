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
-- HUB DATABASE SCHEMA
-- Contains tables used by the jeffrey-hub deployment (LIVE workspaces).
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
    deleted_at              TIMESTAMPTZ,
    PRIMARY KEY (project_id)
);

CREATE INDEX IF NOT EXISTS idx_projects_workspace_id ON projects(workspace_id);
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

-- The file-download hot path joins repositories by repository_id alone, which is not the
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
    -- Whether anything in this session will report liveness (the jeffrey-heartbeat library).
    -- Declared by the provisioner rather than detected: whether the library is on an application's
    -- class path is a build-time fact. Nullable on purpose: NULL means the session was declared by
    -- a provisioner too old to say, and only a TRUE session is held to the heartbeat deadline
    -- (see SessionFinisher).
    heartbeat_expected    BOOLEAN,
    -- What this session was actually started with, copied from its marker file. The command is the
    -- fully resolved one, the source names the configuration layer its base came from, and
    -- config_layers is a JSON array of {scope, digest} naming the hub-published files that were
    -- merged. Comparing those digests with what the hub holds now is what says whether a running
    -- JVM is still on the current configuration. All three are NULL for a session declared by a
    -- provisioner too old to record them.
    profiler_command_source VARCHAR,
    profiler_command        VARCHAR,
    config_layers           VARCHAR,
    PRIMARY KEY (repository_id, session_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_instance_sessions_session_path ON project_instance_sessions(repository_id, relative_session_path);
CREATE INDEX IF NOT EXISTS idx_project_instance_sessions_instance_id ON project_instance_sessions(instance_id);
-- The file-download hot path looks sessions up by session_id alone, which is not the
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
-- SCOPED CONFIGURATION TABLE
--

-- One row per scope and configuration type: global (both ids NULL), a workspace (project_id NULL),
-- or a project. The ids stay NULL where the scope has none so that every reader can say IS NULL;
-- the upsert cannot key on them, though, because a UNIQUE over nullable columns treats each NULL
-- as distinct. entry_key is that key, computed by the INSERT from the same values
-- (COALESCE(workspace_id, '') || ':' || COALESCE(project_id, '') || ':' || config_type) - a stored
-- column rather than a generated one because DuckDB does not yet allow a constraint on a generated
-- column.
--
-- No digest column: what identifies a published file is computed from the bytes that were written,
-- so a column here could only ever describe a file that may never have been written.
CREATE TABLE IF NOT EXISTS scoped_configs
(
    scope         VARCHAR   NOT NULL,
    workspace_id  VARCHAR,
    project_id    VARCHAR,
    config_type   VARCHAR   NOT NULL,
    entry_key     VARCHAR   NOT NULL UNIQUE,
    config_value  VARCHAR   NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);
