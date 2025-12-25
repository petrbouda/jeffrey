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

package pbouda.jeffrey.scheduler.job.descriptor;

import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.job.JobType;

import java.util.Map;

public record JobDescriptorFactory() {

    public <T extends JobDescriptor<T>> T create(JobInfo jobInfo) {
        return create(jobInfo.jobType(), jobInfo.params());
    }

    public <T extends JobDescriptor<T>> T create(JobType jobType, Map<String, String> params) {
        return (T) switch (jobType) {
            case PROJECTS_SYNCHRONIZER ->
                    ProjectsSynchronizerJobDescriptor.of(params);
            case REPOSITORY_SESSION_CLEANER ->
                    RepositorySessionCleanerProjectJobDescriptor.of(params);
            case REPOSITORY_RECORDING_CLEANER ->
                    RepositoryRecordingCleanerJobDescriptor.of(params);
            case WORKSPACE_EVENTS_REPLICATOR ->
                    new WorkspaceEventsReplicatorJobDescriptor();
            case WORKSPACE_PROFILER_SETTINGS_SYNCHRONIZER ->
                    WorkspaceProfilerSettingsSynchronizerJobDescriptor.of(params);
            case REPOSITORY_JFR_COMPRESSION ->
                    new RepositoryCompressionProjectJobDescriptor();
            case PROJECT_RECORDING_STORAGE_SYNCHRONIZER ->
                    new ProjectRecordingStorageSynchronizerJobDescriptor();
            case ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER ->
                    new OrphanedProjectRecordingStorageCleanerJobDescriptor();
            default -> throw new IllegalArgumentException("Unsupported job type: " + jobType);
        };
    }
}
