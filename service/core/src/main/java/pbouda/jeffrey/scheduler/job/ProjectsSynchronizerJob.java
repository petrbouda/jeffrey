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
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.workspace.WorkspaceEventConsumerType;
import pbouda.jeffrey.workspace.consumer.CreateProjectWorkspaceEventConsumer;
import pbouda.jeffrey.workspace.consumer.CreateSessionWorkspaceEventConsumer;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProjectsSynchronizerJob extends WorkspaceJob<ProjectsSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);

    private static final WorkspaceEventConsumerType CONSUMER = WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER;

    private final Duration period;
    private final CreateProjectWorkspaceEventConsumer createProjectConsumer;
    private final ProjectsManager projectsManager;

    public ProjectsSynchronizerJob(
            WorkspacesManager workspacesManager,
            ProjectsManager projectsManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.period = period;
        this.createProjectConsumer = new CreateProjectWorkspaceEventConsumer(workspacesManager, projectsManager);
        this.projectsManager = projectsManager;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        WorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();

        CreateSessionWorkspaceEventConsumer createSessionConsumer =
                new CreateSessionWorkspaceEventConsumer(projectsManager);

        List<WorkspaceEvent> workspaceEvents = workspaceManager.workspaceEventManager().remainingEvents(CONSUMER);
        if (workspaceEvents.isEmpty()) {
            LOG.debug("No workspace events to process for workspace: {}", workspaceInfo.id());
            return;
        }

        LOG.info("Processing workspace events for workspace: workspace_id={} size={}",
                workspaceInfo.id(), workspaceEvents.size());

        Map<WorkspaceEventType, List<WorkspaceEvent>> groupedEvents = workspaceEvents.stream()
                .collect(Collectors.groupingBy(WorkspaceEvent::eventType));

        List<WorkspaceEvent> projectEvents = eventsList(groupedEvents, WorkspaceEventType.PROJECT_CREATED);
        List<WorkspaceEvent> sessionEvents = eventsList(groupedEvents, WorkspaceEventType.SESSION_CREATED);

        long latestOffset = -1;

        // Process PROJECT_CREATED events first (dependency requirement)
        LOG.debug("Processing {} PROJECT_CREATED events", projectEvents.size());
        for (WorkspaceEvent event : projectEvents) {
            createProjectConsumer.on(event, jobDescriptor);
            latestOffset = event.eventId();
            LOG.debug("Successfully processed PROJECT_CREATED event: {}", event.eventId());
        }

        // Process SESSION_CREATED events second
        LOG.debug("Processing {} SESSION_CREATED events", sessionEvents.size());
        for (WorkspaceEvent event : sessionEvents) {
            createSessionConsumer.on(event, jobDescriptor);
            latestOffset = event.eventId();
            LOG.debug("Successfully processed SESSION_CREATED event: {}", event.eventId());
        }

        // Update consumer state with the latest processed event timestamp
        if (latestOffset != -1) {
            try {
                workspaceManager.workspaceEventManager().updateConsumer(CONSUMER, latestOffset);

                LOG.info("Updated consumer state for workspace: workspace_id={} consumer={} offset={}",
                        workspaceInfo.id(), CONSUMER, latestOffset);
            } catch (Exception e) {
                LOG.error("Failed to update consumer state for workspace: {}", workspaceInfo.id(), e);
            }
        }
    }

    private static List<WorkspaceEvent> eventsList(
            Map<WorkspaceEventType, List<WorkspaceEvent>> groupedEvents, WorkspaceEventType eventType) {

        return groupedEvents.getOrDefault(eventType, List.of()).stream()
                .sorted(Comparator.comparing(WorkspaceEvent::eventId))
                .toList();
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECTS_SYNCHRONIZER;
    }
}
