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

package pbouda.jeffrey.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventConsumer;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.workspace.WorkspaceEventConsumerType;

import java.util.List;
import java.util.Optional;

public class WorkspaceEventManagerImpl implements WorkspaceEventManager {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventManagerImpl.class);

    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;

    public WorkspaceEventManagerImpl(
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository) {

        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public void batchInsertEvents(List<WorkspaceEvent> events) {
        workspaceRepository.batchInsertEvents(events);
    }

    @Override
    public List<WorkspaceEvent> remainingEvents(WorkspaceEventConsumerType consumerType) {
        WorkspaceEventConsumer consumer = getOrCreateConsumer(consumerType);
        long lastOffset = consumer.lastOffset() != null ? consumer.lastOffset() : 0;
        return workspaceRepository.findEventsFromOffset(lastOffset);
    }

    private WorkspaceEventConsumer getOrCreateConsumer(WorkspaceEventConsumerType consumerType) {
        String workspaceId = workspaceInfo.id();
        String consumerId = consumerType.name();

        Optional<WorkspaceEventConsumer> consumerOpt =
                workspaceRepository.findEventConsumer(consumerId);

        if (consumerOpt.isEmpty()) {
            LOG.info("No consumer found, create a new one: consumer_id={} workspace_id={}",
                    consumerId, workspaceId);
            workspaceRepository.createEventConsumer(consumerId);
            consumerOpt = workspaceRepository.findEventConsumer(consumerId);
            if (consumerOpt.isEmpty()) {
                throw new IllegalStateException(
                        "Failed to create event consumer: consumer_id=" + consumerId + " workspace_id=" + workspaceId);
            }
        }
        return consumerOpt.get();
    }

    @Override
    public void updateConsumer(WorkspaceEventConsumerType consumer, long lastOffset) {
        workspaceRepository.updateEventConsumerOffset(consumer.name(), lastOffset);
    }

    @Override
    public List<WorkspaceEvent> findEvents() {
        return workspaceRepository.findEvents();
    }
}
