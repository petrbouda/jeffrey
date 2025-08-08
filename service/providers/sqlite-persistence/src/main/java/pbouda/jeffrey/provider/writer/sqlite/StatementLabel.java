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

package pbouda.jeffrey.provider.writer.sqlite;

public enum StatementLabel {

    WAL_CHECK_POINT,

    /**
     * {@link GroupLabel#INTERNAL_PROFILES}
     */
    INSERT_PROFILE,
    INITIALIZE_PROFILE,
    MAX_EVENT_TIMESTAMP,
    UPDATE_PROFILE_FINISHED_AT,

    /**
     * {@link GroupLabel#GLOBAL_SCHEDULERS}
     */
    INSERT_GLOBAL_JOB,
    FIND_ALL_GLOBAL_JOBS,
    ENABLE_GLOBAL_JOB,
    DELETE_GLOBAL_JOB,

    /**
     * {@link GroupLabel#PROJECT_RECORDINGS}
     */
    INSERT_FOLDER,
    INSERT_RECORDING,
    INSERT_RECORDING_FILE,
    FOLDER_EXISTS,
    FIND_RECORDING,
    FIND_ALL_RECORDINGS,
    FIND_RECORDING_FILES,
    FIND_RECORDINGS_IN_FOLDER,
    FIND_ALL_RECORDING_FILES,
    FIND_ALL_FOLDERS,
    DELETE_RECORDING,
    DELETE_FOLDER,

    /**
     * {@link GroupLabel#INTERNAL_RECORDINGS}
     */
    FIND_RECORDING_INTERNAL,

    /**
     * {@link GroupLabel#PROFILE_CACHE}
     */
    INSERT_CACHE_ENTRY,
    KEY_EXISTS,
    FIND_CACHE_ENTRY,

    /**
     * {@link GroupLabel#PROFILE_EVENTS}
     */
    FIND_LATEST_EVENT,
    FIND_ALL_LATEST_EVENTS,
    STREAM_EVENTS,

    /**
     * {@link GroupLabel#PROFILE_EVENT_TYPES}
     */
    FIELDS_WITH_EVENT_TYPE,
    FIELDS_WITH_SINGLE_EVENT,
    CONTAINS_EVENT,
    COLUMNS_BY_SINGLE_EVENT,
    EVENT_SUMMARIES,
    FIND_EVENT_TYPE,

    /**
     * {@link GroupLabel#PROFILE_GRAPHS}
     */
    INSERT_GRAPH,
    FIND_GRAPH_CONTENT,
    FIND_ALL_METADATA,
    DELETE_GRAPH,

    /**
     * {@link GroupLabel#PROFILES}
     */
    FIND_PROFILE,
    ENABLED_PROFILE,
    UPDATE_PROFILE_NAME,
    DELETE_PROFILE,

    /**
     * {@link GroupLabel#PROJECTS}
     */
    FIND_ALL_PROJECTS,
    FIND_PROJECTS_BY_WORKSPACE,
    INSERT_PROJECT,

    /**
     * {@link GroupLabel#SINGLE_PROJECT}
     */
    FIND_ALL_PROFILES,
    DELETE_PROJECT,
    FIND_PROJECT,
    UPDATE_PROJECT_NAME,

    /**
     * {@link GroupLabel#PROJECT_REPOSITORIES}
     */
    INSERT_REPOSITORY,
    FIND_ALL_REPOSITORIES,
    DELETE_REPOSITORY,
    DELETE_ALL_REPOSITORIES,

    /**
     * {@link GroupLabel#PROJECT_SCHEDULERS}
     */
    INSERT_SCHEDULER,
    FIND_ALL_SCHEDULERS,
    ENABLE_SCHEDULER,
    DELETE_SCHEDULER,

    /**
     * {@link GroupLabel#EVENT_WRITERS}
     */
    INSERT_EVENT_TYPES,
    INSERT_EVENTS,
    INSERT_STACKTRACES,
    INSERT_STACKTRACE_TAGS,
    INSERT_THREADS,

    /**
     * {@link GroupLabel#NATIVE_LEAK_EVENTS}
     */
    FIND_NATIVE_LEAK_EVENTS,
    FIND_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT,
    FIND_MALLOC_EVENT_TYPE_COLUMNS,
    MALLOC_AND_FREE_EXISTS,

    /**
     * {@link GroupLabel#WORKSPACES}
     */
    FIND_ALL_WORKSPACES,
    FIND_WORKSPACE_BY_ID,
    INSERT_WORKSPACE,
    DELETE_WORKSPACE,
    CHECK_NAME_EXISTS,
    INSERT_WORKSPACE_SESSION,
    FIND_SESSIONS_BY_PROJECT_ID,
    FIND_SESSION_BY_PROJECT_AND_SESSION_ID,

}
