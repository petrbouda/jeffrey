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

package pbouda.jeffrey.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventConsumer;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;
import pbouda.jeffrey.repository.model.RemoteWorkspaceEvent;
import pbouda.jeffrey.workspace.WorkspaceEventConsumerType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class WorkspaceManagerImpl implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceManagerImpl.class);

    private final HomeDirs homeDirs;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;

    public WorkspaceManagerImpl(
            HomeDirs homeDirs,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository) {

        this.homeDirs = homeDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public long replicate(boolean removeReplicatedEvents) {
        Optional<Path> workspacePath = workspacePath();
        if (workspacePath.isEmpty()) {
            LOG.warn("Cannot migrate workspace events: {}", workspaceInfo.id());
            return 0;
        }

        try {
            RemoteWorkspaceRepository remoteRepository = remoteWorkspaceRepository();
            List<RemoteWorkspaceEvent> remoteEvents = remoteRepository.findAllEvents();
            if (remoteEvents.isEmpty()) {
                LOG.debug("No remote workspace events to migrate for workspace {}", workspaceInfo.id());
                return 0;
            }

            List<WorkspaceEvent> workspaceEvents = remoteEvents.stream()
                    .map(this::convertToWorkspaceEvent)
                    .toList();

            workspaceRepository.batchInsertEvents(workspaceEvents);

            if (removeReplicatedEvents) {
                List<String> eventIds = remoteEvents.stream()
                        .map(RemoteWorkspaceEvent::eventId)
                        .toList();
                remoteRepository.deleteEventsByIds(eventIds);
            }

            LOG.info("Successfully migrated remote events: event_counts={} workspace={}",
                    workspaceEvents.size(), workspaceInfo.id());

            return workspaceEvents.size();
        } catch (Exception e) {
            LOG.error("Failed to migrate workspace events for workspace {}", workspaceInfo.id(), e);
            throw new RuntimeException("Failed to migrate workspace events", e);
        }
    }

    @Override
    public WorkspaceInfo info() {
        return workspaceInfo;
    }

    @Override
    public Optional<Path> workspacePath() {
        Path workspacePath = workspaceInfo.path() == null
                ? homeDirs.workspaces().resolve(workspaceInfo.id())
                : Path.of(workspaceInfo.path());

        if (validateWorkspacePath(workspacePath)) {
            return Optional.of(workspacePath);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        return workspacePath().map(RemoteWorkspaceRepository::new)
                .orElseThrow(() -> new IllegalStateException("Workspace path is not set or invalid"));
    }

    @Override
    public void delete() {
        workspaceRepository.delete(workspaceInfo.id());
    }

    @Override
    public List<WorkspaceEvent> findEventsFromOffset(long fromOffset) {
        return workspaceRepository.findEventsFromOffset(workspaceInfo.id(), fromOffset);
    }

    @Override
    public List<WorkspaceEvent> remainingEvents(WorkspaceEventConsumerType consumerType) {
        Optional<WorkspaceEventConsumer> consumerOpt = workspaceRepository.findEventConsumer(consumerType.name());
        if (consumerOpt.isEmpty()) {
            LOG.info("No consumer found, create a new one: consumer_id='{}'", consumerType.name());
            workspaceRepository.createEventConsumer(consumerType.name());
            return remainingEvents(consumerType);
        }
        WorkspaceEventConsumer consumer = consumerOpt.get();
        long lastOffset = consumer.lastOffset() != null ? consumer.lastOffset() : 0;
        return workspaceRepository.findEventsFromOffset(workspaceInfo.id(), lastOffset);
    }

    @Override
    public void updateConsumer(WorkspaceEventConsumerType consumer, long lastOffset) {
        workspaceRepository.updateEventConsumerOffset(consumer.name(), lastOffset);
    }

    @Override
    public void createSession(WorkspaceSessionInfo workspaceSessionInfo) {
        workspaceRepository.createSession(workspaceSessionInfo);
    }

    @Override
    public List<WorkspaceEvent> findEvents() {
        return workspaceRepository.findEvents(workspaceInfo.id());
    }

    private WorkspaceEvent convertToWorkspaceEvent(RemoteWorkspaceEvent remoteEvent) {
        return new WorkspaceEvent(
                null, // ID will be generated by the repository
                remoteEvent.eventId(),
                remoteEvent.projectId(),
                workspaceInfo.id(),
                WorkspaceEventType.valueOf(remoteEvent.eventType()),
                remoteEvent.content(),
                remoteEvent.createdAt(),
                Instant.now()
        );
    }

    public static boolean validateWorkspacePath(Path workspaceDir) {
        return Files.exists(workspaceDir) && Files.isDirectory(workspaceDir);
    }
}
