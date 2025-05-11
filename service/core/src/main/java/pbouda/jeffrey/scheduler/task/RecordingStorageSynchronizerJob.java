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

import pbouda.jeffrey.provider.api.model.job.JobType;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.time.Duration;
import java.util.function.Function;

public class RecordingStorageSynchronizerJob extends Job {

    private final Function<String, ProjectRecordingRepository> recordingRepositoryFactory;
    private final ProjectsRepository projectsRepository;
    private final ProjectRecordingStorage.Factory recordingStorageFactory;

    public RecordingStorageSynchronizerJob(
            Repositories repositories,
            ProjectRecordingStorage.Factory recordingStorageFactory,
            Duration period) {

        super(JobType.RECORDING_STORAGE_SYNCHRONIZER, period);

        this.recordingRepositoryFactory = repositories::newProjectRecordingRepository;
        this.projectsRepository = repositories.newProjectsRepository();
        this.recordingStorageFactory = recordingStorageFactory;
    }

    @Override
    public void run() {

    }
}
