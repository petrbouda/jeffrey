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

package pbouda.jeffrey.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.workspace.CLIWorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.ProjectCreatedEventContent;
import pbouda.jeffrey.shared.common.model.workspace.event.SessionCreatedEventContent;
import pbouda.jeffrey.shared.folderqueue.FolderQueue;

import java.time.Clock;
import java.util.Map;

/**
 * Publishes workspace events to the folder-based event queue. Converts domain
 * objects into {@link CLIWorkspaceEvent} JSON files that Jeffrey's
 * {@code WorkspaceEventsReplicatorJob} will pick up and process.
 */
public class EventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EventPublisher.class);

    private static final String CREATED_BY = "CLI";

    private final FolderQueue folderQueue;
    private final Clock clock;

    public EventPublisher(FolderQueue folderQueue, Clock clock) {
        this.folderQueue = folderQueue;
        this.clock = clock;
    }

    public void publishProjectCreated(
            String projectId,
            String workspaceId,
            String projectName,
            String projectLabel,
            String workspacesPath,
            RepositoryType repositoryType,
            Map<String, String> attributes) {

        ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                projectName, projectLabel, workspacesPath,
                workspaceId, projectName, repositoryType, attributes);

        publish(projectId, projectId, workspaceId,
                WorkspaceEventType.PROJECT_CREATED, Json.toString(content));
    }

    public void publishInstanceCreated(
            String instanceId,
            String projectId,
            String workspaceId) {

        InstanceCreatedEventContent content = new InstanceCreatedEventContent(instanceId);

        publish(instanceId, projectId, workspaceId,
                WorkspaceEventType.PROJECT_INSTANCE_CREATED, Json.toString(content));
    }

    public void publishSessionCreated(
            String sessionId,
            String projectId,
            String workspaceId,
            String instanceId,
            int order,
            String profilerSettings) {

        String relativeSessionPath = instanceId + "/" + sessionId;

        SessionCreatedEventContent content = new SessionCreatedEventContent(
                instanceId, order, relativeSessionPath, profilerSettings);

        publish(sessionId, projectId, workspaceId,
                WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, Json.toString(content));
    }

    private void publish(
            String originEventId,
            String projectId,
            String workspaceId,
            WorkspaceEventType eventType,
            String content) {

        CLIWorkspaceEvent event = new CLIWorkspaceEvent(
                originEventId, projectId, workspaceId,
                eventType, content, clock.instant(), CREATED_BY);

        folderQueue.publish(originEventId, Json.toString(event));
        LOG.debug("Published workspace event: eventType={} originEventId={}", eventType, originEventId);
    }
}
