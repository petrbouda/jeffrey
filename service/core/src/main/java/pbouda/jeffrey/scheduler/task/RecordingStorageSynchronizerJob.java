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

package pbouda.jeffrey.scheduler.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.model.job.JobType;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class RecordingStorageSynchronizerJob extends Job {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingStorageSynchronizerJob.class);

    private final Function<String, ProjectRecordingRepository> recordingRepositoryFactory;
    private final ProjectsRepository projectsRepository;
    private final RecordingStorage recordingStorage;

    public RecordingStorageSynchronizerJob(
            Repositories repositories,
            RecordingStorage recordingStorage,
            Duration period) {

        super(JobType.RECORDING_STORAGE_SYNCHRONIZER, period);

        this.recordingRepositoryFactory = repositories::newProjectRecordingRepository;
        this.projectsRepository = repositories.newProjectsRepository();
        this.recordingStorage = recordingStorage;
    }

    @Override
    public void run() {
        List<String> projectsInStorage = recordingStorage.findAllProjects();
        List<ProjectInfo> projectsInDatabase = projectsRepository.findAllProjects();

        for (String project : projectsInStorage) {
            ProjectRecordingStorage projectRecordingStorage = recordingStorage.projectRecordingStorage(project);

            // Try to find the project in database, Optional#empty() if the project has been already removed from DB
            Optional<ProjectInfo> projectInDatabaseOpt = projectsInDatabase.stream()
                    .filter(projectInfo -> projectInfo.id().equals(project))
                    .findFirst();

            // The entire project has been already deleted from the database, remove it from the storage.
            if (projectInDatabaseOpt.isEmpty()) {
                projectRecordingStorage.delete();
                LOG.info("Removed project from Recording Storage: project_id={}", project);
                continue;
            }

            ProjectRecordingRepository recordingRepository = recordingRepositoryFactory.apply(project);

            List<String> recordingsInDatabase =
                    recordingRepository.findAllRecordings().stream()
                            .map(Recording::id)
                            .toList();
            List<String> recordingsInStorage = projectRecordingStorage.findAllRecordingIds();

            // The size of both collections is the same, then no adding or removing happened during the last Job period
            // The "storage" size is bigger, then some recording was removed from the database
            // - initial size: 5 / 5
            // - removed from DB: 4 / 5
            // - added new recording: 5 / 6
            if (recordingsInDatabase.size() == recordingsInStorage.size()) {
                continue;
            }

            List<String> difference = new ArrayList<>(recordingsInStorage);
            difference.removeAll(recordingsInDatabase);
            difference.forEach(rec -> {
                projectRecordingStorage.delete(rec);
                LOG.info("Removed recording from Recording Storage: recording_id={} project_name='{}'",
                        rec, projectInDatabaseOpt.get().name());
            });
        }
    }
}
