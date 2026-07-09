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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.workspace.consumer.WorkspaceEventConsumer;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.shared.persistentqueue.QueueEntry;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectsSynchronizerJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");

    private static final String WORKSPACE_ID = "ws-internal-001";
    private static final String CONSUMER_ID = "PROJECT_SYNCHRONIZER_CONSUMER";

    @TempDir
    Path tempDir;

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    WorkspaceEventConsumer consumer;

    @Mock
    @SuppressWarnings("unchecked")
    PersistentQueue<WorkspaceEvent> workspaceEventQueue;

    private ProjectsSynchronizerJob job;

    @BeforeEach
    void setUp() {
        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                WORKSPACE_ID, "ws-ref-001", null, "Test Workspace",
                WorkspaceLocation.of(tempDir), null, NOW, WorkspaceStatus.UNKNOWN, 0);

        doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
        when(workspaceManager.resolveInfo()).thenReturn(workspaceInfo);
        lenient().when(workspaceManager.projectsManager()).thenReturn(projectsManager);
        lenient().when(consumer.isApplicable(any())).thenReturn(true);

        job = new ProjectsSynchronizerJob(
                List.of(consumer), workspaceEventQueue, TransactionOperations.withoutTransaction(),
                workspacesManager, Duration.ofSeconds(30));
    }

    private static QueueEntry<WorkspaceEvent> entry(long offset) {
        WorkspaceEvent event = new WorkspaceEvent(
                null, "origin-" + offset, null, "ws-ref-001",
                WorkspaceEventType.PROJECT_CREATED, "{}", NOW, NOW, "CLI");
        return new QueueEntry<>(offset, event, NOW);
    }

    private void queueReturns(QueueEntry<WorkspaceEvent>... entries) {
        when(workspaceEventQueue.poll(WORKSPACE_ID, CONSUMER_ID)).thenReturn(List.of(entries));
    }

    /**
     * Makes the consumer throw for the event with the given id and succeed for all others.
     */
    private void failOnEvent(long failingEventId) {
        doAnswer(invocation -> {
            WorkspaceEvent event = invocation.getArgument(0);
            if (event.eventId() == failingEventId) {
                throw new RuntimeException("boom on event " + failingEventId);
            }
            return null;
        }).when(consumer).on(any(), eq(projectsManager));
    }

    @Nested
    class SuccessfulProcessing {

        @Test
        void processesAllEvents_andAcknowledgesEachOffset() {
            queueReturns(entry(1), entry(2), entry(3));

            job.execute(JobContext.EMPTY);

            verify(consumer, times(3)).on(any(), eq(projectsManager));
            // Each event is acknowledged within its own processing transaction
            verify(workspaceEventQueue).acknowledge(WORKSPACE_ID, CONSUMER_ID, 1);
            verify(workspaceEventQueue).acknowledge(WORKSPACE_ID, CONSUMER_ID, 2);
            verify(workspaceEventQueue).acknowledge(WORKSPACE_ID, CONSUMER_ID, 3);
        }

        @Test
        void doesNotAcknowledge_whenQueueIsEmpty() {
            when(workspaceEventQueue.poll(WORKSPACE_ID, CONSUMER_ID)).thenReturn(List.of());

            job.execute(JobContext.EMPTY);

            verify(workspaceEventQueue, never()).acknowledge(any(), any(), anyLong());
        }
    }

    @Nested
    class FailedProcessing {

        @Test
        void failedEvent_stopsBatch_andAcknowledgesOnlyPrecedingEvents() {
            queueReturns(entry(1), entry(2), entry(3));
            failOnEvent(2L);

            job.execute(JobContext.EMPTY);

            // Event 1 processed and acknowledged; the failed event 2 stays at the head of the
            // queue, and event 3 must not overtake it
            verify(consumer, times(2)).on(any(), eq(projectsManager));
            verify(workspaceEventQueue).acknowledge(WORKSPACE_ID, CONSUMER_ID, 1);
            verify(workspaceEventQueue, never()).acknowledge(WORKSPACE_ID, CONSUMER_ID, 2);
            verify(workspaceEventQueue, never()).acknowledge(WORKSPACE_ID, CONSUMER_ID, 3);
        }

        @Test
        void failedFirstEvent_acknowledgesNothing() {
            queueReturns(entry(1), entry(2));
            doThrow(new RuntimeException("boom")).when(consumer).on(any(), eq(projectsManager));

            job.execute(JobContext.EMPTY);

            verify(workspaceEventQueue, never()).acknowledge(any(), any(), anyLong());
        }

        @Test
        void failedEvent_isRetriedOnNextTick_andSucceeds() {
            queueReturns(entry(1));
            doThrow(new RuntimeException("transient"))
                    .doNothing()
                    .when(consumer).on(any(), eq(projectsManager));

            job.execute(JobContext.EMPTY);
            verify(workspaceEventQueue, never()).acknowledge(any(), any(), anyLong());

            job.execute(JobContext.EMPTY);
            verify(workspaceEventQueue).acknowledge(WORKSPACE_ID, CONSUMER_ID, 1);
        }

        @Test
        void poisonEvent_isDroppedAfterMaxAttempts_andProcessingContinues() {
            queueReturns(entry(1), entry(2));
            failOnEvent(1L);

            // Four failed attempts keep the queue blocked at the poison event
            for (int i = 0; i < 4; i++) {
                job.execute(JobContext.EMPTY);
            }
            verify(workspaceEventQueue, never()).acknowledge(any(), any(), anyLong());

            // The fifth attempt exhausts the retry budget: the poison event is dropped
            // (acknowledged without processing) and the following event is finally
            // processed and acknowledged
            job.execute(JobContext.EMPTY);

            verify(consumer).on(argThat(e -> e != null && e.eventId() == 2L), eq(projectsManager));
            verify(workspaceEventQueue).acknowledge(WORKSPACE_ID, CONSUMER_ID, 1);
            verify(workspaceEventQueue).acknowledge(WORKSPACE_ID, CONSUMER_ID, 2);
        }
    }
}
