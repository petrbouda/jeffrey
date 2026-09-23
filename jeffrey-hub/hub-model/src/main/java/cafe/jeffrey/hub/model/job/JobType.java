/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.model.job;

public enum JobType {
    WORKSPACE_RECONCILER(ExecutionLevel.GLOBAL),
    TEMP_DIRECTORY_CLEANER(ExecutionLevel.GLOBAL),
    DELETED_PROJECTS_CLEANER(ExecutionLevel.GLOBAL),
    STORAGE_OVERVIEW_REFRESHER(ExecutionLevel.GLOBAL),
    PROJECT_INSTANCE_SESSION_CLEANER(ExecutionLevel.PROJECT),
    PROJECT_STORAGE_QUOTA_CLEANER(ExecutionLevel.PROJECT),
    EXPIRED_INSTANCE_CLEANER(ExecutionLevel.PROJECT),
    REPOSITORY_JFR_COMPRESSION(ExecutionLevel.PROJECT),
    SESSION_FINISHED_DETECTOR(ExecutionLevel.PROJECT);

    /**
     * Where a job runs in the server's execution model:
     * <ul>
     *   <li>{@link #GLOBAL} — singleton tick, no fan-out</li>
     *   <li>{@link #PROJECT} — fan-out across all projects in all workspaces</li>
     * </ul>
     */
    public enum ExecutionLevel {
        GLOBAL, PROJECT
    }

    private final ExecutionLevel executionLevel;

    JobType(ExecutionLevel executionLevel) {
        this.executionLevel = executionLevel;
    }

    public ExecutionLevel executionLevel() {
        return executionLevel;
    }
}
