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

package cafe.jeffrey.hub.core.workspace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotifyingWorkspaceEventQueueTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-06-15T12:00:00Z");

    @Mock
    PersistentQueue<WorkspaceEvent> delegate;

    private final List<String> notifications = new ArrayList<>();
    private final WorkspaceEventNotifier notifier = new WorkspaceEventNotifier();

    private NotifyingWorkspaceEventQueue newQueue() {
        notifier.addListener(notifications::add);
        return new NotifyingWorkspaceEventQueue(delegate, notifier);
    }

    private static WorkspaceEvent event() {
        return new WorkspaceEvent(null, "origin-1", "proj-1", WORKSPACE_ID,
                WorkspaceEventType.PROJECT_CREATED, null, FIXED_TIME, FIXED_TIME, "test");
    }

    @Nested
    class Notification {

        @Test
        void singleAppendNotifiesItsScope() {
            var queue = newQueue();

            queue.append(WORKSPACE_ID, event());

            assertAll(
                    () -> verify(delegate).append(WORKSPACE_ID, event()),
                    () -> assertEquals(List.of(WORKSPACE_ID), notifications));
        }

        @Test
        void batchAppendNotifiesOnce() {
            var queue = newQueue();

            queue.appendBatch(WORKSPACE_ID, List.of(event(), event()));

            assertEquals(List.of(WORKSPACE_ID), notifications,
                    "A batch is one wakeup, not one per event");
        }

        @Test
        void emptyBatchDoesNotNotify() {
            var queue = newQueue();

            queue.appendBatch(WORKSPACE_ID, List.of());

            assertEquals(List.of(), notifications);
        }

        @Test
        void aFailingListenerDoesNotBreakTheAppend() {
            notifier.addListener(_ -> {
                throw new IllegalStateException("listener is broken");
            });
            var queue = newQueue();

            queue.append(WORKSPACE_ID, event());

            assertAll(
                    () -> verify(delegate).append(WORKSPACE_ID, event()),
                    () -> assertEquals(List.of(WORKSPACE_ID), notifications,
                            "A broken listener must not stop the remaining listeners"));
        }
    }

    @Nested
    class Delegation {

        @Test
        void readsAndRetentionPassThroughWithoutNotifying() {
            var queue = newQueue();

            queue.poll(WORKSPACE_ID, "consumer-1");
            queue.acknowledge(WORKSPACE_ID, "consumer-1", 5L);
            queue.findAll(WORKSPACE_ID, 10);
            queue.findFromOffset(WORKSPACE_ID, 3L, 10);
            queue.count(WORKSPACE_ID);
            queue.deleteEventsOlderThan(FIXED_TIME);

            assertAll(
                    () -> verify(delegate).poll(WORKSPACE_ID, "consumer-1"),
                    () -> verify(delegate).acknowledge(WORKSPACE_ID, "consumer-1", 5L),
                    () -> verify(delegate).findAll(WORKSPACE_ID, 10),
                    () -> verify(delegate).findFromOffset(WORKSPACE_ID, 3L, 10),
                    () -> verify(delegate).count(WORKSPACE_ID),
                    () -> verify(delegate).deleteEventsOlderThan(FIXED_TIME),
                    () -> assertEquals(List.of(), notifications, "Only appends wake subscribers"));
        }
    }
}
