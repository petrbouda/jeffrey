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

package cafe.jeffrey.shared.common.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsChangeDispatcherTest {

    private static final String OBSERVED = "jeffrey.microscope.ai.provider";
    private static final String UNOBSERVED = "logging.level.cafe.jeffrey";

    private static final Map<String, String> DEFAULTS = Map.of(OBSERVED, "none", UNOBSERVED, "INFO");

    /** Records how many times it was applied and the value it saw on the last apply. */
    private static final class RecordingListener implements SettingsChangeListener {

        private final Set<String> observed;
        private final AtomicInteger applies = new AtomicInteger();
        private final AtomicReference<String> lastSeen = new AtomicReference<>();

        private RecordingListener(Set<String> observed) {
            this.observed = observed;
        }

        @Override
        public Set<String> observedSettings() {
            return observed;
        }

        @Override
        public void onChanged(SettingsStore store) {
            lastSeen.set(store.get(OBSERVED));
            applies.incrementAndGet();
        }
    }

    @Nested
    class Filtering {

        @Test
        void listenerIsAppliedWhenItObservesTheChangedSetting() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            RecordingListener listener = new RecordingListener(Set.of(OBSERVED));

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of(listener))) {
                dispatcher.changed(Set.of(OBSERVED));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, listener.applies.get()));
            }
        }

        @Test
        void listenerIsSkippedWhenItObservesNothingThatChanged() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            RecordingListener observing = new RecordingListener(Set.of(OBSERVED));
            RecordingListener ignoring = new RecordingListener(Set.of(UNOBSERVED));

            try (SettingsChangeDispatcher dispatcher =
                         new SettingsChangeDispatcher(store, () -> List.of(observing, ignoring))) {
                dispatcher.changed(Set.of(OBSERVED));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, observing.applies.get()));
                assertEquals(0, ignoring.applies.get());
            }
        }

        @Test
        void emptyChangeSetAppliesNothing() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            RecordingListener listener = new RecordingListener(Set.of(OBSERVED));

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of(listener))) {
                dispatcher.changed(Set.of());

                await().during(Duration.ofMillis(500))
                        .atMost(5, SECONDS)
                        .untilAsserted(() -> assertEquals(0, listener.applies.get()));
            }
        }
    }

    @Nested
    class Debouncing {

        @Test
        void burstOfChangesIsAppliedOnce() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            RecordingListener listener = new RecordingListener(Set.of(OBSERVED));

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of(listener))) {
                for (int i = 0; i < 5; i++) {
                    dispatcher.changed(Set.of(OBSERVED));
                }

                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, listener.applies.get()));
            }
        }

        @Test
        void appliedRunObservesTheFinalValueOfTheBurst() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            RecordingListener listener = new RecordingListener(Set.of(OBSERVED));

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of(listener))) {
                store.put(OBSERVED, "claude");
                dispatcher.changed(Set.of(OBSERVED));
                store.put(OBSERVED, "ollama");
                dispatcher.changed(Set.of(OBSERVED));

                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals("ollama", listener.lastSeen.get()));
                assertEquals(1, listener.applies.get());
            }
        }

        @Test
        void separatedChangesAreAppliedSeparately() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            RecordingListener listener = new RecordingListener(Set.of(OBSERVED));

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of(listener))) {
                dispatcher.changed(Set.of(OBSERVED));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, listener.applies.get()));

                dispatcher.changed(Set.of(OBSERVED));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(2, listener.applies.get()));
            }
        }
    }

    @Nested
    class Isolation {

        @Test
        void failingListenerDoesNotStopTheNextOne() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            SettingsChangeListener failing = new SettingsChangeListener() {
                @Override
                public Set<String> observedSettings() {
                    return Set.of(OBSERVED);
                }

                @Override
                public void onChanged(SettingsStore ignored) {
                    throw new IllegalStateException("rebuild failed");
                }
            };
            RecordingListener healthy = new RecordingListener(Set.of(OBSERVED));

            try (SettingsChangeDispatcher dispatcher =
                         new SettingsChangeDispatcher(store, () -> List.of(failing, healthy))) {
                dispatcher.changed(Set.of(OBSERVED));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, healthy.applies.get()));
            }
        }

        @Test
        void dispatcherKeepsWorkingAfterAListenerFails() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            AtomicInteger attempts = new AtomicInteger();
            SettingsChangeListener flaky = new SettingsChangeListener() {
                @Override
                public Set<String> observedSettings() {
                    return Set.of(OBSERVED);
                }

                @Override
                public void onChanged(SettingsStore ignored) {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("first apply fails");
                    }
                }
            };

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of(flaky))) {
                dispatcher.changed(Set.of(OBSERVED));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, attempts.get()));

                dispatcher.changed(Set.of(OBSERVED));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(2, attempts.get()));
            }
        }
    }

    @Nested
    class DeferredCleanup {

        @Test
        void cleanupRunsAfterTheDelay() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            AtomicInteger closed = new AtomicInteger();

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of())) {
                dispatcher.scheduleCleanup(closed::incrementAndGet, Duration.ofMillis(50));
                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, closed.get()));
            }
        }

        @Test
        void failingCleanupIsSwallowed() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of());
            AtomicInteger after = new AtomicInteger();

            try (SettingsChangeDispatcher dispatcher = new SettingsChangeDispatcher(store, () -> List.of())) {
                dispatcher.scheduleCleanup(() -> {
                    throw new IllegalStateException("close failed");
                }, Duration.ofMillis(10));
                dispatcher.scheduleCleanup(after::incrementAndGet, Duration.ofMillis(50));

                await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, after.get()));
            }
        }
    }
}
