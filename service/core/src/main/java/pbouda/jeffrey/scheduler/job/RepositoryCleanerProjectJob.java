/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.RepositoryCleanerJobDescriptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class RepositoryCleanerProjectJob extends RepositoryProjectJob<RepositoryCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryCleanerProjectJob.class);
    private static final JobType JOB_TYPE = JobType.REPOSITORY_CLEANER;

    public RepositoryCleanerProjectJob(
            ProjectsManager projectsManager,
            RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {
        super(projectsManager, remoteRepositoryManagerFactory, jobDescriptorFactory, JOB_TYPE, period);
    }

    protected void executeOnRepository(
            ProjectManager manager,
            RemoteRepositoryStorage remoteRepositoryStorage,
            RepositoryCleanerJobDescriptor jobDescriptor) {

        String projectName = manager.info().name();
        LOG.info("Cleaning the repository: project='{}'", projectName);
        Duration duration = jobDescriptor.toDuration();

        Instant currentTime = Instant.now();
        List<RecordingSession> candidatesForDeletion = remoteRepositoryStorage.listSessions().stream()
                .filter(session -> currentTime.isAfter(session.finishedAt().plus(duration)))
                .toList();

        candidatesForDeletion.forEach(session -> {
            remoteRepositoryStorage.deleteSession(session.id());
            LOG.info("Deleted recording from the repository: project='{}' session={}", projectName, session.id());
        });
    }
}
