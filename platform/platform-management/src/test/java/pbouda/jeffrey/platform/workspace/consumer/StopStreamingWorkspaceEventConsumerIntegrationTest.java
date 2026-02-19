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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StopStreamingWorkspaceEventConsumerIntegrationTest {

    private static final String WORKSPACE_ID = "ws-001";
    private static final String SESSION_ID = "session-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static WorkspaceEvent sessionFinishedEvent(String sessionId) {
        return new WorkspaceEvent(null, sessionId, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, Json.EMPTY,
                NOW, NOW, "test");
    }

    @Nested
    class HappyPath {

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void sessionFinished_unregistersConsumer() {
            var consumer = new StopStreamingWorkspaceEventConsumer(streamingConsumerManager);
            consumer.on(sessionFinishedEvent(SESSION_ID), JOB_DESCRIPTOR);

            verify(streamingConsumerManager).unregisterConsumer(SESSION_ID);
        }
    }

    @Nested
    class IsApplicable {

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void onlyApplicable_forSessionFinishedEvents() {
            var consumer = new StopStreamingWorkspaceEventConsumer(streamingConsumerManager);

            WorkspaceEvent sessionFinished = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, null, NOW, NOW, "test");
            WorkspaceEvent sessionCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, null, NOW, NOW, "test");
            WorkspaceEvent projectDeleted = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_DELETED, null, NOW, NOW, "test");

            assertTrue(consumer.isApplicable(sessionFinished));
            assertFalse(consumer.isApplicable(sessionCreated));
            assertFalse(consumer.isApplicable(projectDeleted));
        }
    }
}
