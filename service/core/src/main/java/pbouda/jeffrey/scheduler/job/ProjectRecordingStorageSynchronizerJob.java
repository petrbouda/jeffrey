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

package pbouda.jeffrey.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectRecordingStorageSynchronizerJobDescriptor;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Scheduler job that synchronizes recording storage with the database.
 * Removes orphaned recordings from storage that no longer exist in the database.
 */
public class ProjectRecordingStorageSynchronizerJob extends ProjectJob<ProjectRecordingStorageSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRecordingStorageSynchronizerJob.class);

    private final Function<String, ProjectRecordingRepository> recordingRepositoryFactory;
    private final RecordingStorage recordingStorage;
    private final Duration period;

    public ProjectRecordingStorageSynchronizerJob(
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Repositories repositories,
            RecordingStorage recordingStorage,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.recordingRepositoryFactory = repositories::newProjectRecordingRepository;
        this.recordingStorage = recordingStorage;
        this.period = period;
    }

    @Override
    protected void execute(ProjectManager projectManager, ProjectRecordingStorageSynchronizerJobDescriptor jobDescriptor) {
        String projectId = projectManager.info().id();
        String projectName = projectManager.info().name();

        ProjectRecordingStorage projectRecordingStorage = recordingStorage.projectRecordingStorage(projectId);
        ProjectRecordingRepository recordingRepository = recordingRepositoryFactory.apply(projectId);

        List<String> recordingsInDatabase = recordingRepository.findAllRecordings().stream()
                .map(Recording::id)
                .toList();
        List<String> recordingsInStorage = projectRecordingStorage.findAllRecordingIds();

        // No difference in size means no sync needed
        if (recordingsInDatabase.size() == recordingsInStorage.size()) {
            LOG.debug("Recording storage in sync with database: project='{}'", projectName);
            return;
        }

        // Find recordings in storage that don't exist in database
        List<String> orphanedRecordings = new ArrayList<>(recordingsInStorage);
        orphanedRecordings.removeAll(recordingsInDatabase);

        orphanedRecordings.forEach(recordingId -> {
            projectRecordingStorage.delete(recordingId);
            LOG.info("Removed orphaned recording from storage: recording_id={} project='{}'",
                    recordingId, projectName);
        });
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECT_RECORDING_STORAGE_SYNCHRONIZER;
    }
}
