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

package cafe.jeffrey.hub.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.InstanceLifecycleEventEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectInstanceSessionCleanerJobDescriptor;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectInstanceSessionCleanerJob extends RepositoryProjectJob<ProjectInstanceSessionCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstanceSessionCleanerJob.class);

    private final Duration period;
    private final Clock clock;
    private final HubPlatformRepositories platformRepositories;
    private final InstanceLifecycleEventEmitter instanceLifecycleEventEmitter;

    public ProjectInstanceSessionCleanerJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory remoteRepositoryManagerFactory,
            ProjectInstanceSessionCleanerJobDescriptor jobDescriptor,
            Duration period,
            Clock clock,
            HubPlatformRepositories platformRepositories,
            InstanceLifecycleEventEmitter instanceLifecycleEventEmitter) {
        super(workspacesManager, remoteRepositoryManagerFactory, jobDescriptor);
        this.period = period;
        this.clock = clock;
        this.platformRepositories = platformRepositories;
        this.instanceLifecycleEventEmitter = instanceLifecycleEventEmitter;
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
        int maxSessions = jobDescriptor.maxSessions();

        Instant currentTime = clock.instant();
        ProjectInstanceRepository instanceRepo = platformRepositories.newProjectInstanceRepository(projectId);

        // Group ALL non-retained sessions by instance — the still-active session must occupy
        // a slot of the max-sessions cap, so it stays in the list and is excluded only at
        // deletion time. Retained sessions are pinned evidence (a manual pin, or an auto-pin
        // from a detected JVM crash): they never expire and never consume a cap slot.
        // Files must be loaded (listSessions(true)) so isFailedEmpty() sees real sizes.
        Map<String, List<RecordingSession>> sessionsByInstance = repositoryStorage.listSessions(true).stream()
                .filter(session -> !session.retained())
                .collect(Collectors.groupingBy(RecordingSession::instanceId));

        List<RecordingSession> candidatesForDeletion = new ArrayList<>();

        for (var entry : sessionsByInstance.entrySet()) {
            String instanceId = entry.getKey();
            InstanceSessionUnits units = InstanceSessionUnits.of(entry.getValue());

            Optional<ProjectInstanceInfo> instanceOpt = instanceRepo.find(instanceId);
            boolean isFinished = instanceOpt.isPresent()
                    && instanceOpt.get().status() == ProjectInstanceStatus.FINISHED;

            // Protection slot: the newest FINISHED session that actually produced data.
            // Failed/empty sessions (finished with zero bytes, e.g. a crash-looped container)
            // never consume the keep-newest protection, and the active session must not claim
            // it either — it is already safe, and letting it win would silently un-protect
            // the newest real finished session.
            String protectedSessionId = units.sessionsNewestFirst().stream()
                    .filter(session -> session.finishedAt() != null)
                    .filter(session -> !session.isFailedEmpty())
                    .map(RecordingSession::id)
                    .findFirst()
                    .orElse(null);

            // Two independent rules in one pass: the age window, and the per-instance cap of
            // logical sessions (a consecutive crash-loop run counts as one unit). Sessions in
            // units beyond the cap are deleted even before their age window expires.
            List<List<RecordingSession>> unitList = units.units();
            for (int unitIndex = 0; unitIndex < unitList.size(); unitIndex++) {
                boolean overCap = unitIndex >= maxSessions;
                for (RecordingSession session : unitList.get(unitIndex)) {
                    if (session.finishedAt() == null) {
                        continue;
                    }

                    // Keep the newest non-empty session for non-FINISHED instances
                    if (!isFinished && session.id().equals(protectedSessionId)) {
                        continue;
                    }

                    boolean ageExpired = currentTime.isAfter(session.createdAt().plus(duration));
                    if (ageExpired || overCap) {
                        candidatesForDeletion.add(session);
                    }
                }
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
                        instanceLifecycleEventEmitter.emitInstanceExpired(
                                manager.info(), instanceId, currentTime,
                                WorkspaceEventCreator.PROJECT_INSTANCE_SESSION_CLEANER_JOB);
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
