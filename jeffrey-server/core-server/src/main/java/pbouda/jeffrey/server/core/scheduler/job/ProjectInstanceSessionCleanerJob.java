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

package pbouda.jeffrey.server.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.core.jfr.JfrMessageEmitter;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.server.core.project.repository.RepositoryStorage;
import pbouda.jeffrey.server.core.scheduler.JobContext;
import pbouda.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.server.core.scheduler.job.descriptor.ProjectInstanceSessionCleanerJobDescriptor;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.server.persistence.repository.ProjectInstanceRepository;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectInstanceSessionCleanerJob extends RepositoryProjectJob<ProjectInstanceSessionCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstanceSessionCleanerJob.class);

    private final Duration period;
    private final Clock clock;
    private final ServerPlatformRepositories platformRepositories;

    public ProjectInstanceSessionCleanerJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period,
            Clock clock,
            ServerPlatformRepositories platformRepositories) {
        super(workspacesManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
        this.period = period;
        this.clock = clock;
        this.platformRepositories = platformRepositories;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RepositoryStorage repositoryStorage,
            ProjectInstanceSessionCleanerJobDescriptor jobDescriptor,
            JobContext context) {

        String projectId = manager.info().id();
        String projectName = manager.info().name();
        LOG.debug("Cleaning the project instance sessions: project='{}'", projectName);
        Duration duration = jobDescriptor.toDuration();

        Instant currentTime = clock.instant();
        ProjectInstanceRepository instanceRepo = platformRepositories.newProjectInstanceRepository(projectId);

        // Group finished sessions by instance
        Map<String, List<RecordingSession>> sessionsByInstance = repositoryStorage.listSessions(false).stream()
                .filter(session -> session.finishedAt() != null)
                .collect(Collectors.groupingBy(RecordingSession::instanceId));

        List<RecordingSession> candidatesForDeletion = new ArrayList<>();

        for (var entry : sessionsByInstance.entrySet()) {
            String instanceId = entry.getKey();
            List<RecordingSession> sessions = entry.getValue().stream()
                    .sorted(Comparator.comparing(RecordingSession::createdAt).reversed())
                    .toList();

            Optional<ProjectInstanceInfo> instanceOpt = instanceRepo.find(instanceId);
            boolean isFinished = instanceOpt.isPresent()
                    && instanceOpt.get().status() == ProjectInstanceStatus.FINISHED;

            for (int i = 0; i < sessions.size(); i++) {
                RecordingSession session = sessions.get(i);
                if (!currentTime.isAfter(session.createdAt().plus(duration))) {
                    continue;
                }

                // Keep latest session for non-FINISHED instances (skip(1) per instance)
                if (i == 0 && !isFinished) {
                    continue;
                }

                candidatesForDeletion.add(session);
            }
        }

        candidatesForDeletion.forEach(session -> {
            manager.repositoryManager()
                    .deleteRecordingSession(session.id(), WorkspaceEventCreator.PROJECT_INSTANCE_SESSION_CLEANER_JOB);
            LOG.info("Deleted recording from the project instance session: project='{}' session={}", projectName, session.id());
        });

        if (!candidatesForDeletion.isEmpty()) {
            JfrMessageEmitter.sessionsCleaned(projectName, candidatesForDeletion.size());

            // Set expiring_at on affected instances (first session deletion)
            Set<String> affectedInstanceIds = candidatesForDeletion.stream()
                    .map(RecordingSession::instanceId)
                    .collect(Collectors.toSet());

            for (String instanceId : affectedInstanceIds) {
                Optional<ProjectInstanceInfo> instanceOpt = instanceRepo.find(instanceId);
                if (instanceOpt.isPresent()) {
                    ProjectInstanceInfo instance = instanceOpt.get();
                    if (instance.expiringAt() == null) {
                        instanceRepo.setExpiringAt(instanceId, currentTime);
                    }

                    // Check if instance has 0 remaining sessions → EXPIRED
                    if (instance.status() == ProjectInstanceStatus.FINISHED
                            && instance.sessionCount() == 0) {
                        instanceRepo.updateStatusAndExpiredAt(instanceId, ProjectInstanceStatus.EXPIRED, currentTime);
                        LOG.info("Instance marked as EXPIRED (last session deleted): instanceId={} projectId={}",
                                instanceId, projectId);
                    }
                }
            }
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECT_INSTANCE_SESSION_CLEANER;
    }
}
