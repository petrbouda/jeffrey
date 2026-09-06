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

package cafe.jeffrey.profile.manager;

import tools.jackson.core.type.TypeReference;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.shared.common.CacheKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoAnalysisManagerImplTest {

    private static final Path RECORDING = Path.of("/recordings/app.jfr");

    private final ProfileCacheRepository cacheRepository = mock(ProfileCacheRepository.class);

    private static AutoAnalysisResult result(String rule, Severity severity) {
        return new AutoAnalysisResult(rule, severity, "explanation", "summary", "solution", "50");
    }

    private AutoAnalysisManagerImpl manager(
            Supplier<Optional<Path>> resolver, Function<Path, List<AutoAnalysisResult>> ruleSet) {

        return new AutoAnalysisManagerImpl(cacheRepository, resolver, ruleSet);
    }

    private AutoAnalysisManagerImpl manager(Function<Path, List<AutoAnalysisResult>> ruleSet) {
        return manager(() -> Optional.of(RECORDING), ruleSet);
    }

    @Nested
    @DisplayName("Availability")
    class Availability {

        @Test
        @DisplayName("follows whether the recording file resolves")
        void followsTheResolver() {
            assertTrue(manager(_ -> List.of()).canGenerate());
            assertFalse(manager(Optional::empty, _ -> List.of()).canGenerate());
        }

        @Test
        @DisplayName("generating without a recording fails rather than caching an empty analysis")
        void generatingWithoutARecordingFails() {
            AutoAnalysisManagerImpl manager = manager(Optional::empty, _ -> List.of());

            assertThrows(IllegalStateException.class, manager::generate);
            verify(cacheRepository, never()).put(any(), any());
        }
    }

    @Nested
    @DisplayName("Generating")
    class Generating {

        @Test
        @DisplayName("orders the findings by severity and caches them")
        void ordersAndCaches() {
            List<AutoAnalysisResult> unordered = List.of(
                    result("ignored", Severity.IGNORE),
                    result("passed", Severity.OK),
                    result("warned", Severity.WARNING),
                    result("informed", Severity.INFO));

            List<AutoAnalysisResult> results = manager(recording -> {
                assertEquals(RECORDING, recording);
                return unordered;
            }).generate();

            assertEquals(
                    List.of("warned", "informed", "passed", "ignored"),
                    results.stream().map(AutoAnalysisResult::rule).toList());
            verify(cacheRepository).put(eq(CacheKey.PROFILE_AUTO_ANALYSIS), eq(results));
        }
    }

    @Nested
    @DisplayName("Single flight")
    class SingleFlight {

        /**
         * The point of the guard: a run holds the whole recording in the JMC item model, so a second
         * concurrent run would hold a second copy of it. The warm-up starts one at import, and the
         * page's button or the MCP tool can arrive while it is still going.
         */
        @Test
        @DisplayName("a caller arriving mid-run joins it instead of starting a second one")
        void concurrentCallersShareOneRun() throws Exception {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger runs = new AtomicInteger();

            AutoAnalysisManagerImpl manager = manager(_ -> {
                runs.incrementAndGet();
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(result("warned", Severity.WARNING));
            });

            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<List<AutoAnalysisResult>> outcomes = new ArrayList<>(List.of(List.of(), List.of()));
            AtomicReference<Thread> joiner = new AtomicReference<>();
            try {
                var first = executor.submit(manager::generate);
                assertTrue(started.await(2, TimeUnit.SECONDS), "the first run never started");

                var second = executor.submit(() -> {
                    joiner.set(Thread.currentThread());
                    return manager.generate();
                });

                // The second caller parks on the run in flight rather than evaluating anything.
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                    Thread thread = joiner.get();
                    assertTrue(thread != null && (thread.getState() == Thread.State.WAITING
                                    || thread.getState() == Thread.State.TIMED_WAITING),
                            "the second caller is not parked on the run in flight");
                });
                assertEquals(1, runs.get(), "the second caller started a run of its own");

                release.countDown();
                outcomes.set(0, first.get(5, TimeUnit.SECONDS));
                outcomes.set(1, second.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
                executor.shutdownNow();
            }

            assertEquals(1, runs.get(), "the rule set ran more than once");
            assertEquals(outcomes.get(0), outcomes.get(1));
        }

        @Test
        @DisplayName("a failed run is not remembered, so the next caller can try again")
        void aFailedRunDoesNotPoisonTheNextOne() {
            AtomicInteger runs = new AtomicInteger();
            AutoAnalysisManagerImpl manager = manager(_ -> {
                if (runs.incrementAndGet() == 1) {
                    throw new IllegalStateException("rules blew up");
                }
                return List.of(result("warned", Severity.WARNING));
            });

            assertThrows(IllegalStateException.class, manager::generate);
            assertEquals(1, manager.generate().size());
            assertEquals(2, runs.get());
        }
    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        @Test
        @DisplayName("an analysis that was never computed reads as empty, not as a failure")
        void emptyWhenNeverComputed() {
            when(cacheRepository.get(eq(CacheKey.PROFILE_AUTO_ANALYSIS), any(TypeReference.class)))
                    .thenReturn(Optional.empty());

            assertEquals(List.of(), manager(_ -> List.of()).analysisResults());
        }
    }
}
