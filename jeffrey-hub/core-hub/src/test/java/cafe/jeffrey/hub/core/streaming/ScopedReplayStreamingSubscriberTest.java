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

package cafe.jeffrey.hub.core.streaming;

import cafe.jeffrey.hub.api.v1.EventBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedReplayStreamingSubscriberTest {
    @Test
    void emptyTypeFilterCountsAllObservedTypes(@TempDir Path temp) throws Exception {
        var subscription = new ReplayStreamSubscription("session", List.of(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1)),
                Set.of(), StreamingWindow.UNBOUNDED, temp, "workspace", "project");
        Set<String> types = java.util.concurrent.ConcurrentHashMap.newKeySet();
        CountDownLatch closed = new CountDownLatch(1);
        var reader = new ReplayStreamingSubscriber(subscription, new StreamingCallbacks(
                batch -> batch.getEventsList().forEach(event -> types.add(event.getEventType())),
                () -> {}, error -> {}, closed::countDown));
        reader.start();
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        assertTrue(types.size() > 1, "aggregation without a type filter must discover event types");
    }

    @Test
    void strictReplayPreservesTypesAndTimeWindowAndReportsCompleteSource(@TempDir Path temp) throws Exception {
        Instant start = Instant.parse("2025-12-20T00:12:24Z");
        Instant end = start.plusSeconds(300);
        var subscription = new ReplayStreamSubscription("session", List.of(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1)),
                Set.of("jdk.CPULoad"), new StreamingWindow(start, end), temp, "workspace", "project");
        List<EventBatch> batches = new ArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        ReplayStreamingSubscriber reader = new ReplayStreamingSubscriber(subscription,
                new StreamingCallbacks(batches::add, () -> {}, error::set, closed::countDown));
        reader.start();
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        assertEquals(null, error.get());
        assertEquals("project", batches.getFirst().getReplayStatus().getProjectId());
        assertTrue(batches.getLast().getReplayStatus().getTerminal());
        assertEquals(0, batches.getLast().getReplayStatus().getSourceErrors());
        var events = batches.stream().flatMap(batch -> batch.getEventsList().stream()).toList();
        assertTrue(events.size() > 100 && events.size() < 400);
        assertTrue(events.stream().allMatch(event -> event.getEventType().equals("jdk.CPULoad")
                && event.getTimestamp() >= start.toEpochMilli() && event.getTimestamp() <= end.toEpochMilli()));
        try (var files = Files.list(temp)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void cancellationStopsBeforeNextFileAndCleansScratchAfterExit(@TempDir Path temp) throws Exception {
        var subscription = new ReplayStreamSubscription("session", List.of(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1),
                JfrTestFiles.resolve(JfrTestFiles.PROFILE_2)), Set.of("jdk.CPULoad"), StreamingWindow.UNBOUNDED,
                temp, "workspace", "project");
        List<EventBatch> batches = new ArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<ReplayStreamingSubscriber> reader = new AtomicReference<>();
        reader.set(new ReplayStreamingSubscriber(subscription, new StreamingCallbacks(batch -> {
            batches.add(batch);
            if (batch.getEventsCount() > 0) {
                reader.get().close();
            }
        }, () -> completed.set(true), error -> {}, closed::countDown)));
        reader.get().start();
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        assertFalse(completed.get());
        assertEquals(1, batches.stream().filter(batch -> batch.getEventsCount() > 0).count());
        assertFalse(batches.getLast().hasReplayStatus());
        try (var files = Files.list(temp)) {
            assertEquals(0, files.count());
        }
    }
}
