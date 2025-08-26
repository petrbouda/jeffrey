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

package pbouda.jeffrey.common.model.job;

public enum JobType {
    REPOSITORY_CLEANER(Group.PROJECT),
    INTERVAL_RECORDING_GENERATOR(Group.PROJECT),
    PERIODIC_RECORDING_GENERATOR(Group.PROJECT),
    COPY_RECORDING_GENERATOR(Group.PROJECT),
    PROJECTS_SYNCHRONIZER(Group.GLOBAL),
    RECORDING_STORAGE_SYNCHRONIZER(Group.INTERNAL),
    WORKSPACE_EVENTS_REPLICATOR(Group.WORKSPACE_EVENTS),
    FILESYSTEM_EVENTS_REPLICATOR(Group.WORKSPACE_EVENTS),;

    public enum Group {
        PROJECT, GLOBAL, INTERNAL, WORKSPACE_EVENTS
    }

    private final Group group;

    JobType(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }
}
