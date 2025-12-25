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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.repository.RemoteWorkspaceRepository;
import pbouda.jeffrey.platform.repository.model.RemoteProject;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.WorkspaceEventsReplicatorJobDescriptor;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;

import java.time.Clock;
import java.time.Duration;
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
                    workspaceManager.resolveInfo().id(), e);
        }
    }

    private long replicateFilesystemEvents(WorkspaceManager workspaceManager) {
        WorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();

        LOG.debug("Starting filesystem events replication for workspace: " +
                "workspace_id={} repository_id={}", workspaceInfo.id(), workspaceInfo.repositoryId());

        RemoteWorkspaceRepository remoteWorkspaceRepository = workspaceManager.remoteWorkspaceRepository();
        List<RemoteProject> allProjects = remoteWorkspaceRepository.allProjects();

        List<WorkspaceEvent> projectEvents = replicateProjects(workspaceManager, allProjects);
        List<WorkspaceEvent> sessionEvents = replicateSessions(workspaceManager, allProjects);

        int processedEvents = projectEvents.size() + sessionEvents.size();
        if (processedEvents > 0) {
            LOG.info("Replicated filesystem events for: workspace_id={} repository_id={} projects={}, sessions={}",
                    workspaceInfo.id(), workspaceInfo.repositoryId(), projectEvents.size(), sessionEvents.size());
        }
        return processedEvents;
    }

    private List<WorkspaceEvent> replicateProjects(WorkspaceManager workspaceManager, List<RemoteProject> allProjects) {
        List<WorkspaceEvent> projectWorkspaceEvents = allProjects.stream()
                .filter(event -> !processedProjects.contains(event.projectId()))
                .map(event -> {
                    return WorkspaceEventConverter.projectCreated(
                            clock.instant(),
                            event,
                            workspaceManager.resolveInfo(),
                            WorkspaceEventCreator.WORKSPACE_EVENTS_REPLICATOR_JOB);
                })
                .toList();

        workspaceManager.workspaceEventManager().batchInsertEvents(projectWorkspaceEvents);

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
                .map(event -> {
                    return WorkspaceEventConverter.sessionCreated(
                            clock.instant(),
                            event,
                            workspaceManager.resolveInfo(),
                            WorkspaceEventCreator.WORKSPACE_EVENTS_REPLICATOR_JOB);
                })
                .toList();

        workspaceManager.workspaceEventManager().batchInsertEvents(sessionWorkspaceEvents);

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
}
