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
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.WorkspaceManager;
import pbouda.jeffrey.manager.WorkspacesManager;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;
import pbouda.jeffrey.repository.model.RemoteProject;
import pbouda.jeffrey.repository.model.RemoteSession;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.WorkspaceEventsReplicatorJobDescriptor;
import pbouda.jeffrey.workspace.model.ProjectCreatedEventContent;
import pbouda.jeffrey.workspace.model.SessionCreatedEventContent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorkspaceEventsReplicatorJob extends WorkspaceJob<WorkspaceEventsReplicatorJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsReplicatorJob.class);

    private final Duration period;
    private final Clock clock;
    private final Runnable migrationCallback;

    private final Set<String> processedProjects = new HashSet<>();
    private final Set<String> processedSessions = new HashSet<>();

    public WorkspaceEventsReplicatorJob(
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period,
            Clock clock,
            Runnable migrationCallback) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.period = period;
        this.clock = clock;
        this.migrationCallback = migrationCallback;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager,
            WorkspaceEventsReplicatorJobDescriptor jobDescriptor) {

        try {
            long migrated = replicateFilesystemEvents(workspaceManager);

            if (migrated > 0) {
                // Execute after successful migration
                migrationCallback.run();
            }
        } catch (Exception e) {
            LOG.error("Failed to replicate filesystem events for workspace: {}",
                    workspaceManager.info().id(), e);
        }
    }

    private long replicateFilesystemEvents(WorkspaceManager workspaceManager) {
        String workspaceId = workspaceManager.info().id();

        LOG.debug("Starting filesystem events replication for workspace: {}", workspaceId);

        RemoteWorkspaceRepository remoteWorkspaceRepository = workspaceManager.remoteWorkspaceRepository();
        List<RemoteProject> allProjects = remoteWorkspaceRepository.allProjects();

        List<WorkspaceEvent> projectEvents = replicateProjects(workspaceManager, allProjects);
        List<WorkspaceEvent> sessionEvents = replicateSessions(workspaceManager, allProjects);

        LOG.info("Replicated filesystem events for workspace: {}, projects: {}, sessions: {}",
                workspaceId, projectEvents.size(), sessionEvents.size());

        return projectEvents.size() + sessionEvents.size();
    }

    private List<WorkspaceEvent> replicateProjects(WorkspaceManager workspaceManager, List<RemoteProject> allProjects) {
        List<WorkspaceEvent> projectWorkspaceEvents = allProjects.stream()
                .filter(event -> !processedProjects.contains(event.projectId()))
                .map(this::convertToWorkspaceEvent)
                .toList();

        workspaceManager.batchInsertEvents(projectWorkspaceEvents);

        projectWorkspaceEvents.stream()
                .map(WorkspaceEvent::projectId)
                .forEach(processedProjects::add);

        return projectWorkspaceEvents;
    }

    private List<WorkspaceEvent> replicateSessions(WorkspaceManager workspaceManager, List<RemoteProject> allProjects) {
        RemoteWorkspaceRepository remoteWorkspaceRepository = workspaceManager.remoteWorkspaceRepository();

        List<WorkspaceEvent> sessionWorkspaceEvents = allProjects.stream()
                .map(remoteWorkspaceRepository::allSessions)
                .flatMap(List::stream)
                .filter(event -> !processedSessions.contains(event.sessionId()))
                .map(this::convertToWorkspaceEvent)
                .toList();

        workspaceManager.batchInsertEvents(sessionWorkspaceEvents);

        sessionWorkspaceEvents.stream()
                .map(WorkspaceEvent::originEventId)
                .forEach(processedSessions::add);

        return sessionWorkspaceEvents;
    }


    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.WORKSPACE_EVENTS_REPLICATOR;
    }

    private WorkspaceEvent convertToWorkspaceEvent(RemoteProject event) {
        ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                event.projectName(),
                event.projectLabel(),
                event.repositoryType(),
                event.attributes());

        return new WorkspaceEvent(
                null, // ID will be generated by the repository
                event.projectId(),
                event.projectId(),
                event.workspaceId(),
                WorkspaceEventType.PROJECT_CREATED,
                Json.toString(content),
                Instant.ofEpochMilli(event.createdAt()),
                clock.instant()
        );
    }

    private WorkspaceEvent convertToWorkspaceEvent(RemoteSession event) {
        SessionCreatedEventContent content = new SessionCreatedEventContent(
                event.relativePath(),
                event.workspacesPath());

        return new WorkspaceEvent(
                null, // ID will be generated by the repository
                event.sessionId(),
                event.projectId(),
                event.workspaceId(),
                WorkspaceEventType.SESSION_CREATED,
                Json.toString(content),
                Instant.ofEpochMilli(event.createdAt()),
                clock.instant()
        );
    }
}
