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

package cafe.jeffrey.shared.common.model.job;

public enum JobType {
    WORKSPACE_EVENTS_REPLICATOR(ExecutionLevel.GLOBAL),
    WORKSPACE_EVENTS_CLEANER(ExecutionLevel.GLOBAL),
    PROJECTS_SYNCHRONIZER(ExecutionLevel.WORKSPACE),
    PROFILER_SETTINGS_SYNCHRONIZER(ExecutionLevel.WORKSPACE),
    PROJECT_INSTANCE_SESSION_CLEANER(ExecutionLevel.PROJECT),
    PROJECT_INSTANCE_RECORDING_CLEANER(ExecutionLevel.PROJECT),
    EXPIRED_INSTANCE_CLEANER(ExecutionLevel.PROJECT),
    REPOSITORY_JFR_COMPRESSION(ExecutionLevel.PROJECT),
    SESSION_FINISHED_DETECTOR(ExecutionLevel.PROJECT);

    /**
     * Where a job runs in the server's three-level execution model:
     * <ul>
     *   <li>{@link #GLOBAL} — singleton tick, no fan-out</li>
     *   <li>{@link #WORKSPACE} — fan-out across all workspaces</li>
     *   <li>{@link #PROJECT} — fan-out across all projects in all workspaces</li>
     * </ul>
     */
    public enum ExecutionLevel {
        GLOBAL, WORKSPACE, PROJECT
    }

    private final ExecutionLevel executionLevel;

    JobType(ExecutionLevel executionLevel) {
        this.executionLevel = executionLevel;
    }

    public ExecutionLevel executionLevel() {
        return executionLevel;
    }
}
