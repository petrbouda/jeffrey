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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceEventStreamingManagerTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-06-15T12:00:00Z");

    /** Long enough that the backstop never fires unless a test wants it to. */
    private static final Duration SLOW_BACKSTOP = Duration.ofMinutes(5);

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<WorkspaceEventStreamingManager> managers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        managers.forEach(WorkspaceEventStreamingManager::close);
        executor.shutdownNow();
    }

    /**
     * An append-only reader that mimics the queue: monotonic offsets, ascending reads with an
     * exclusive lower bound, and no consumer registration.
     */
    private static class FakeReader implements WorkspaceEventReader {

        private final List<WorkspaceEvent> events = new CopyOnWriteArrayList<>();
        private final AtomicInteger reads = new AtomicInteger();

        void add(WorkspaceEventType eventType) {
            add(eventType, "proj-1");
        }

        void add(WorkspaceEventType eventType, String projectId) {
            long offset = events.size() + 1L;
            events.add(new WorkspaceEvent(offset, "origin-" + offset, projectId, WORKSPACE_ID,
                    eventType, null, FIXED_TIME, FIXED_TIME, "test"));
        }

        @Override
        public List<WorkspaceEvent> findAll(String workspaceId, int limit) {
            throw new UnsupportedOperationException("streaming must not use findAll");
        }

        @Override
        public List<WorkspaceEvent> findFromOffset(String workspaceId, long fromOffset, int limit) {
            reads.incrementAndGet();
            if (!WORKSPACE_ID.equals(workspaceId)) {
                return List.of();
            }
            List<WorkspaceEvent> matching = events.stream()
                    .filter(event -> event.eventId() > fromOffset)
                    .toList();
            if (limit > 0 && matching.size() > limit) {
                return matching.subList(0, limit);
            }
            return matching;
        }

        @Override
        public long count(String workspaceId) {
            return events.size();
        }
    }

    private record Recorder(
            List<WorkspaceEventPage> pages,
            AtomicInteger completions,
            AtomicReference<Throwable> error,
            AtomicInteger closes) {

        static Recorder create() {
            return new Recorder(new CopyOnWriteArrayList<>(), new AtomicInteger(),
                    new AtomicReference<>(), new AtomicInteger());
        }

        WorkspaceEventCallbacks callbacks() {
            return new WorkspaceEventCallbacks(
                    pages::add,
                    completions::incrementAndGet,
                    error::set,
                    closes::incrementAndGet);
        }

        List<WorkspaceEvent> deliveredEvents() {
            return pages.stream().flatMap(page -> page.events().stream()).toList();
        }

        long highestLastOffset() {
            return pages.stream().mapToLong(WorkspaceEventPage::lastOffset).max().orElse(-1L);
        }
    }

    private WorkspaceEventStreamingManager manager(
            WorkspaceEventReader reader, WorkspaceEventNotifier notifier, Duration backstop) {

        var created = new WorkspaceEventStreamingManager(reader, notifier, executor, backstop);
        managers.add(created);
        return created;
    }

    private static WorkspaceEventSubscription subscription(long fromOffset, Set<WorkspaceEventType> types) {
        return new WorkspaceEventSubscription(WORKSPACE_ID, fromOffset, types, Set.of(), false, 100);
    }

    private static WorkspaceEventSubscription projectSubscription(Set<String> projectIds) {
        return new WorkspaceEventSubscription(WORKSPACE_ID, 0, Set.of(), projectIds, false, 100);
    }

    @Nested
    class CatchUp {

        @Test
        void deliversBacklogFromOffsetInAscendingOrder() {
            var reader = new FakeReader();
            reader.add(WorkspaceEventType.PROJECT_CREATED);
            reader.add(WorkspaceEventType.PROJECT_INSTANCE_CREATED);
            reader.add(WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED);
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)
                    .subscribe(subscription(0, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(List.of(1L, 2L, 3L),
                            recorder.deliveredEvents().stream().map(WorkspaceEvent::eventId).toList()));
        }

        @Test
        void fromOffsetIsExclusive() {
            var reader = new FakeReader();
            reader.add(WorkspaceEventType.PROJECT_CREATED);
            reader.add(WorkspaceEventType.PROJECT_DELETED);
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)
                    .subscribe(subscription(1, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(List.of(2L),
                            recorder.deliveredEvents().stream().map(WorkspaceEvent::eventId).toList()));
        }

        @Test
        void caughtUpFlagFlipsExactlyOnce() {
            var reader = new FakeReader();
            reader.add(WorkspaceEventType.PROJECT_CREATED);
            var notifier = new WorkspaceEventNotifier();
            var recorder = Recorder.create();
            var streamingManager = manager(reader, notifier, SLOW_BACKSTOP);

            streamingManager.subscribe(subscription(0, Set.of()), recorder.callbacks());
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertTrue(recorder.pages().stream().anyMatch(WorkspaceEventPage::caughtUp)));

            reader.add(WorkspaceEventType.PROJECT_DELETED);
            notifier.onAppended(WORKSPACE_ID);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(2, recorder.deliveredEvents().size()));

            assertEquals(1, recorder.pages().stream().filter(WorkspaceEventPage::caughtUp)
                            .filter(page -> page.events().isEmpty()).count(),
                    "Exactly one empty catch-up marker page is expected");
        }

        @Test
        void pagesBacklogByBatchSize() {
            var reader = new FakeReader();
            for (int i = 0; i < 5; i++) {
                reader.add(WorkspaceEventType.PROJECT_CREATED);
            }
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP).subscribe(
                    new WorkspaceEventSubscription(WORKSPACE_ID, 0, Set.of(), Set.of(), false, 2),
                    recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(5, recorder.deliveredEvents().size()));

            List<Integer> batchSizes = recorder.pages().stream()
                    .map(page -> page.events().size())
                    .filter(size -> size > 0)
                    .toList();
            assertEquals(List.of(2, 2, 1), batchSizes);
        }
    }

    @Nested
    class Tailing {

        @Test
        void notificationTriggersDelivery() {
            var reader = new FakeReader();
            var notifier = new WorkspaceEventNotifier();
            var recorder = Recorder.create();
            manager(reader, notifier, SLOW_BACKSTOP).subscribe(subscription(0, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertTrue(recorder.pages().stream().anyMatch(WorkspaceEventPage::caughtUp)));

            reader.add(WorkspaceEventType.PROJECT_CREATED);
            notifier.onAppended(WORKSPACE_ID);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(1, recorder.deliveredEvents().size()));
        }

        @Test
        void notificationForAnotherWorkspaceIsIgnored() {
            var reader = new FakeReader();
            var notifier = new WorkspaceEventNotifier();
            var recorder = Recorder.create();
            manager(reader, notifier, SLOW_BACKSTOP).subscribe(subscription(0, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertTrue(recorder.pages().stream().anyMatch(WorkspaceEventPage::caughtUp)));

            reader.add(WorkspaceEventType.PROJECT_CREATED);
            notifier.onAppended("some-other-workspace");

            // Nothing should arrive: the subscriber for ws-1 must not be woken by ws-2's append.
            await().during(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .atMost(5, SECONDS)
                    .untilAsserted(() -> assertTrue(recorder.deliveredEvents().isEmpty()));
        }

        @Test
        void backstopDrainsWithoutANotification() {
            var reader = new FakeReader();
            var recorder = Recorder.create();
            // A missed notification (e.g. an append that had not committed yet) must self-heal.
            manager(reader, new WorkspaceEventNotifier(), Duration.ofMillis(100))
                    .subscribe(subscription(0, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertTrue(recorder.pages().stream().anyMatch(WorkspaceEventPage::caughtUp)));

            reader.add(WorkspaceEventType.PROJECT_CREATED);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(1, recorder.deliveredEvents().size()));
        }
    }

    @Nested
    class TypeFiltering {

        @Test
        void onlyRequestedTypesAreDelivered() {
            var reader = new FakeReader();
            reader.add(WorkspaceEventType.PROJECT_CREATED);
            reader.add(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED);
            reader.add(WorkspaceEventType.PROJECT_DELETED);
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP).subscribe(
                    subscription(0, Set.of(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED)),
                    recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(List.of(2L),
                            recorder.deliveredEvents().stream().map(WorkspaceEvent::eventId).toList()));
        }

        @Test
        void emptyFilterMeansAllTypes() {
            var reader = new FakeReader();
            reader.add(WorkspaceEventType.PROJECT_CREATED);
            reader.add(WorkspaceEventType.PROJECT_INSTANCE_EXPIRED);
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)
                    .subscribe(subscription(0, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(2, recorder.deliveredEvents().size()));
        }

        @Test
        void lastOffsetAdvancesEvenWhenEveryEventIsFilteredOut() {
            var reader = new FakeReader();
            for (int i = 0; i < 4; i++) {
                reader.add(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED);
            }
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)
                    .subscribe(subscription(0, Set.of(WorkspaceEventType.PROJECT_CREATED)), recorder.callbacks());

            // Without this, a filtered subscriber would rescan the same filtered-out events on
            // every reconnect and its resume offset would never progress.
            await().atMost(5, SECONDS).untilAsserted(() -> assertAll(
                    () -> assertTrue(recorder.deliveredEvents().isEmpty(), "No event matches the filter"),
                    () -> assertEquals(4L, recorder.highestLastOffset(),
                            "lastOffset must advance past filtered-out events")));
        }
    }

    @Nested
    class ProjectFiltering {

        @Test
        void onlyRequestedProjectsAreDelivered() {
            var reader = new FakeReader();
            reader.add(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED, "proj-1");
            reader.add(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED, "proj-2");
            reader.add(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED, "proj-1");
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)
                    .subscribe(projectSubscription(Set.of("proj-1")), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(List.of(1L, 3L),
                            recorder.deliveredEvents().stream().map(WorkspaceEvent::eventId).toList()));
        }

        @Test
        void severalProjectsCanBeWatchedAtOnce() {
            var reader = new FakeReader();
            reader.add(WorkspaceEventType.PROJECT_CREATED, "proj-1");
            reader.add(WorkspaceEventType.PROJECT_CREATED, "proj-2");
            reader.add(WorkspaceEventType.PROJECT_CREATED, "proj-3");
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)
                    .subscribe(projectSubscription(Set.of("proj-1", "proj-3")), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(List.of(1L, 3L),
                            recorder.deliveredEvents().stream().map(WorkspaceEvent::eventId).toList()));
        }

        @Test
        void lastOffsetAdvancesPastOtherProjectsEvents() {
            var reader = new FakeReader();
            for (int i = 0; i < 4; i++) {
                reader.add(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED, "noisy-project");
            }
            var recorder = Recorder.create();

            manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)
                    .subscribe(projectSubscription(Set.of("quiet-project")), recorder.callbacks());

            // Without this a project-scoped subscriber on a busy workspace would rescan every other
            // project's events after each reconnect, and its resume offset would never move.
            await().atMost(5, SECONDS).untilAsserted(() -> assertAll(
                    () -> assertTrue(recorder.deliveredEvents().isEmpty()),
                    () -> assertEquals(4L, recorder.highestLastOffset(),
                            "lastOffset must advance past other projects' events")));
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void unsubscribeStopsTheSubscriberAndRunsOnClose() {
            var reader = new FakeReader();
            var recorder = Recorder.create();
            var streamingManager = manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP);

            String id = streamingManager.subscribe(subscription(0, Set.of()), recorder.callbacks());
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(1, streamingManager.activeSubscriptions()));

            streamingManager.unsubscribe(id);

            await().atMost(5, SECONDS).untilAsserted(() -> assertAll(
                    () -> assertEquals(1, recorder.closes().get()),
                    () -> assertEquals(0, streamingManager.activeSubscriptions())));
        }

        @Test
        void closeStopsEverySubscriber() {
            var reader = new FakeReader();
            var first = Recorder.create();
            var second = Recorder.create();
            var streamingManager = manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP);
            streamingManager.subscribe(subscription(0, Set.of()), first.callbacks());
            streamingManager.subscribe(subscription(0, Set.of()), second.callbacks());

            streamingManager.close();

            await().atMost(5, SECONDS).untilAsserted(() -> assertAll(
                    () -> assertEquals(1, first.closes().get()),
                    () -> assertEquals(1, second.closes().get())));
        }

        @Test
        void subscriptionCapIsEnforced() {
            var reader = new FakeReader();
            var streamingManager = manager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP);
            for (int i = 0; i < WorkspaceEventStreamingManager.MAX_SUBSCRIPTIONS; i++) {
                streamingManager.subscribe(subscription(0, Set.of()), Recorder.create().callbacks());
            }

            assertThrows(WorkspaceEventStreamingManager.TooManySubscriptionsException.class, () ->
                    streamingManager.subscribe(subscription(0, Set.of()), Recorder.create().callbacks()));
        }
    }

    @Nested
    class SubscriptionValidation {

        @Test
        void rejectsInvalidParameters() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () ->
                            new WorkspaceEventSubscription(" ", 0, Set.of(), Set.of(), false, 10)),
                    () -> assertThrows(IllegalArgumentException.class, () ->
                            new WorkspaceEventSubscription(WORKSPACE_ID, -1, Set.of(), Set.of(), false, 10)),
                    () -> assertThrows(IllegalArgumentException.class, () ->
                            new WorkspaceEventSubscription(WORKSPACE_ID, 0, Set.of(), Set.of(), false, 0)));
        }

        @Test
        void nullFiltersMeanEverything() {
            var created = new WorkspaceEventSubscription(WORKSPACE_ID, 0, null, null, false, 10);

            assertAll(
                    () -> assertEquals(Set.of(), created.eventTypes()),
                    () -> assertEquals(Set.of(), created.projectIds()),
                    () -> assertTrue(created.accepts(
                            event(1L, WorkspaceEventType.PROJECT_CREATED, "any-project"))));
        }

        @Test
        void filtersCombineAsAnd() {
            var created = new WorkspaceEventSubscription(WORKSPACE_ID, 0,
                    Set.of(WorkspaceEventType.PROJECT_CREATED), Set.of("proj-1"), false, 10);

            assertAll(
                    () -> assertTrue(created.accepts(
                            event(1L, WorkspaceEventType.PROJECT_CREATED, "proj-1"))),
                    () -> assertFalse(created.accepts(
                            event(2L, WorkspaceEventType.PROJECT_CREATED, "proj-2")),
                            "Right type, wrong project"),
                    () -> assertFalse(created.accepts(
                            event(3L, WorkspaceEventType.PROJECT_DELETED, "proj-1")),
                            "Right project, wrong type"));
        }

        @Test
        void nullProjectIdIsRejectedByAProjectFilter() {
            var created = new WorkspaceEventSubscription(WORKSPACE_ID, 0, Set.of(), Set.of("proj-1"), false, 10);

            assertFalse(created.accepts(
                    new WorkspaceEvent(1L, "origin", null, WORKSPACE_ID,
                            WorkspaceEventType.PROJECT_CREATED, null, FIXED_TIME, FIXED_TIME, "test")));
        }

        private static WorkspaceEvent event(long id, WorkspaceEventType type, String projectId) {
            return new WorkspaceEvent(id, "origin-" + id, projectId, WORKSPACE_ID,
                    type, null, FIXED_TIME, FIXED_TIME, "test");
        }
    }
}
