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

package pbouda.jeffrey.platform.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEventType;

/**
 * Workspace event consumer that stops JFR streaming when a session finishes.
 */
public class StopStreamingWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(StopStreamingWorkspaceEventConsumer.class);

    private final JfrStreamingConsumerManager streamingConsumerManager;

    public StopStreamingWorkspaceEventConsumer(JfrStreamingConsumerManager streamingConsumerManager) {
        this.streamingConsumerManager = streamingConsumerManager;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        String sessionId = event.originEventId();
        LOG.debug("Stopping streaming for finished session: sessionId={}", sessionId);
        streamingConsumerManager.unregisterConsumer(sessionId);
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.SESSION_FINISHED;
    }
}
