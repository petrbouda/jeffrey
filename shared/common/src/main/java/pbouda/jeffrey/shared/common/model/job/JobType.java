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

package pbouda.jeffrey.shared.common.model.job;

public enum JobType {
    REPOSITORY_SESSION_CLEANER(Group.PROJECT),
    REPOSITORY_RECORDING_CLEANER(Group.PROJECT),
    REPOSITORY_JFR_COMPRESSION(Group.PROJECT),
    SESSION_FINISH_DETECTOR(Group.PROJECT),
    INTERVAL_RECORDING_GENERATOR(Group.PROJECT),
    PERIODIC_RECORDING_GENERATOR(Group.PROJECT),
    COPY_RECORDING_GENERATOR(Group.PROJECT),
    PROJECT_RECORDING_STORAGE_SYNCHRONIZER(Group.PROJECT),

    PROJECTS_SYNCHRONIZER(Group.GLOBAL),
    WORKSPACE_EVENTS_REPLICATOR(Group.GLOBAL),
    WORKSPACE_PROFILER_SETTINGS_SYNCHRONIZER(Group.GLOBAL),
    ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER(Group.GLOBAL);

    public enum Group {
        PROJECT, GLOBAL
    }

    private final Group group;

    JobType(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }
}
