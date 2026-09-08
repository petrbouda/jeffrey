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

package cafe.jeffrey.profile.manager.action;

import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileDataInitializerImplTest {

    private static final String PROFILE_ID = "profile-1";

    private static final List<AutoAnalysisResult> FINDINGS = List.of(new AutoAnalysisResult(
            "Long GC Pauses", AnalysisResult.Severity.WARNING, "explanation", "summary", "solution",
            "78", "garbage_collection"));

    private final ThreadManager threadManager = mock(ThreadManager.class);
    private final AutoAnalysisManager autoAnalysisManager = mock(AutoAnalysisManager.class);

    /** Stands in for the JMC rule set, which the initializer now runs itself rather than through a manager. */
    @SuppressWarnings("unchecked")
    private final Function<Path, List<AutoAnalysisResult>> ruleSet = mock(Function.class);

    /** Records whether the lease taken for the warming was handed back. */
    private final AtomicBoolean leaseReleased = new AtomicBoolean();
    private final AtomicInteger leasesTaken = new AtomicInteger();

    private DatabaseManager databaseManager() {
        DatabaseManager databaseManager = mock(DatabaseManager.class);
        when(databaseManager.acquire(PROFILE_ID)).thenAnswer(_ -> {
            leasesTaken.incrementAndGet();
            return new DatabaseLease(mock(DataSource.class), () -> leaseReleased.set(true));
        });
        return databaseManager;
    }

    private ProfileInfo profileInfo(RecordingEventSource source) {
        ProfileInfo profileInfo = mock(ProfileInfo.class);
        when(profileInfo.id()).thenReturn(PROFILE_ID);
        when(profileInfo.eventSource()).thenReturn(source);
        return profileInfo;
    }

    private ProfileManager profileManager(RecordingEventSource source) {
        // Built before the stubbing below starts: Mockito reads a nested when() as an unfinished
        // stub of the outer one.
        ProfileInfo profileInfo = profileInfo(source);

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.info()).thenReturn(profileInfo);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(profileManager.autoAnalysisManager()).thenReturn(autoAnalysisManager);
        return profileManager;
    }

    private ProfileDataInitializerImpl initializer() {
        return new ProfileDataInitializerImpl(databaseManager(), Runnable::run, ruleSet);
    }

    private static Path recording(Path directory) throws IOException {
        return Files.writeString(directory.resolve("recording.jfr"), "not really a recording");
    }

    /**
     * The half that runs before the parse. It reads the recording file and nothing the parse
     * produces, which is what lets it overlap with it.
     */
    @Nested
    @DisplayName("Starting the analysis")
    class StartingTheAnalysis {

        @Test
        @DisplayName("runs the rule set over the recording file")
        void runsTheRuleSet(@TempDir Path directory) throws Exception {
            Path recording = recording(directory);
            when(ruleSet.apply(recording)).thenReturn(FINDINGS);

            List<AutoAnalysisResult> results = initializer()
                    .startAutoAnalysis(profileInfo(RecordingEventSource.JDK), recording)
                    .get(5, TimeUnit.SECONDS);

            assertEquals(FINDINGS, results);
        }

        /**
         * Nothing of the profile exists yet at this point, so the only thing that can be asked is
         * whether the file is there. A recording that has been deleted is not a failure to report.
         */
        @Test
        @DisplayName("is skipped when the recording file is gone")
        void skippedWhenTheRecordingIsGone(@TempDir Path directory) throws Exception {
            Path missing = directory.resolve("gone.jfr");

            List<AutoAnalysisResult> results = initializer()
                    .startAutoAnalysis(profileInfo(RecordingEventSource.JDK), missing)
                    .get(5, TimeUnit.SECONDS);

            assertNull(results);
            verify(ruleSet, never()).apply(any());
        }

        /**
         * pprof and OTLP imports carry stack samples and no JFR events, and the rule set understands
         * only JFR.
         */
        @Test
        @DisplayName("is skipped for a flamegraph-only import")
        void skippedForAFlamegraphOnlyImport(@TempDir Path directory) throws Exception {
            List<AutoAnalysisResult> results = initializer()
                    .startAutoAnalysis(profileInfo(RecordingEventSource.PPROF), recording(directory))
                    .get(5, TimeUnit.SECONDS);

            assertNull(results);
            verify(ruleSet, never()).apply(any());
        }

        /**
         * The import stage waits for this run, so a rule set that blows up must come back as no
         * findings rather than as a failed future: the analysis is a cache, and the import is not.
         */
        @Test
        @DisplayName("reports a failure as no findings, never as a failed future")
        void failureComesBackAsNoFindings(@TempDir Path directory) throws Exception {
            Path recording = recording(directory);
            when(ruleSet.apply(recording)).thenThrow(new IllegalStateException("rules blew up"));

            List<AutoAnalysisResult> results = initializer()
                    .startAutoAnalysis(profileInfo(RecordingEventSource.JDK), recording)
                    .get(5, TimeUnit.SECONDS);

            assertNull(results);
        }
    }

    @Nested
    @DisplayName("Warming")
    class Warming {

        @Test
        @DisplayName("warms the cached views, stores the findings, and releases the lease")
        void warmsTheViewsAndReleasesTheLease() throws Exception {
            initializer()
                    .initialize(profileManager(RecordingEventSource.JDK),
                            CompletableFuture.completedFuture(FINDINGS))
                    .get(5, TimeUnit.SECONDS);

            verify(threadManager).threadRows();
            verify(autoAnalysisManager).store(FINDINGS);
            assertEquals(1, leasesTaken.get());
            assertTrue(leaseReleased.get(), "the warming lease was never released");
        }

        /**
         * The rules never run twice. What the import started before the parse is stored here, and a
         * manager that would have run them again is never asked to.
         */
        @Test
        @DisplayName("stores the run it was handed rather than starting another")
        void storesRatherThanGenerating() throws Exception {
            initializer()
                    .initialize(profileManager(RecordingEventSource.JDK),
                            CompletableFuture.completedFuture(FINDINGS))
                    .get(5, TimeUnit.SECONDS);

            verify(autoAnalysisManager, never()).generate();
        }

        /**
         * A run that was skipped or failed hands back no findings, and there is nothing to cache.
         * Writing an empty list instead would read as a rule set that ran and flagged nothing.
         */
        @Test
        @DisplayName("caches nothing when the analysis produced nothing")
        void cachesNothingWithoutFindings() throws Exception {
            initializer()
                    .initialize(profileManager(RecordingEventSource.JDK),
                            CompletableFuture.completedFuture(null))
                    .get(5, TimeUnit.SECONDS);

            verify(autoAnalysisManager, never()).store(any());
            verify(threadManager).threadRows();
            assertTrue(leaseReleased.get());
        }

        /**
         * The lease is held for as long as the warming runs, not for as long as this call takes. The
         * import stage waits on the returned future, which is what keeps the two ends together.
         */
        @Test
        @DisplayName("holds the lease until the warming finishes, not until it returns")
        void holdsTheLeaseForTheWholeWarming() throws Exception {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch warmStarted = new CountDownLatch(1);
            when(threadManager.threadRows()).thenAnswer(_ -> {
                warmStarted.countDown();
                release.await(5, TimeUnit.SECONDS);
                return List.of();
            });

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                ProfileDataInitializerImpl initializer =
                        new ProfileDataInitializerImpl(databaseManager(), executor, ruleSet);

                CompletableFuture<Void> warming = initializer.initialize(
                        profileManager(RecordingEventSource.JDK),
                        CompletableFuture.completedFuture(FINDINGS));

                assertTrue(warmStarted.await(2, TimeUnit.SECONDS));
                assertFalse(warming.isDone());
                assertFalse(leaseReleased.get(), "the lease must be held while the warming runs");

                release.countDown();
                warming.get(5, TimeUnit.SECONDS);
                assertTrue(leaseReleased.get());
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }

        /**
         * A view is recomputed on a cache miss, so one that fails to warm makes the profile slower,
         * not broken. It must not take the lease down with it, and now that the import stage waits
         * on this future, it must not take the import down either.
         */
        @Test
        @DisplayName("a failing view does not strand the lease or fail the future")
        void failureIsContained() throws Exception {
            when(threadManager.threadRows()).thenThrow(new IllegalStateException("warm blew up"));

            initializer()
                    .initialize(profileManager(RecordingEventSource.JDK),
                            CompletableFuture.completedFuture(FINDINGS))
                    .get(5, TimeUnit.SECONDS);

            assertTrue(leaseReleased.get(), "a failed warm-up stranded the lease");
        }

        /**
         * The two warms are independent: the analysis is the expensive one and the likeliest to
         * blow up, and it must not take the thread bands or the lease with it.
         */
        @Test
        @DisplayName("a failing store still leaves the thread bands warmed and the lease released")
        void failureDoesNotAffectTheOtherWarm() throws Exception {
            doThrow(new IllegalStateException("cache write blew up"))
                    .when(autoAnalysisManager).store(any());

            initializer()
                    .initialize(profileManager(RecordingEventSource.JDK),
                            CompletableFuture.completedFuture(FINDINGS))
                    .get(5, TimeUnit.SECONDS);

            verify(threadManager).threadRows();
            assertTrue(leaseReleased.get(), "a failed analysis stranded the lease");
        }
    }

    @Nested
    @DisplayName("Flamegraph-only imports")
    class FlamegraphOnlyImports {

        @Test
        @DisplayName("are skipped without taking a lease")
        void skipsWithoutTakingALease() throws Exception {
            DatabaseManager databaseManager = databaseManager();
            ProfileDataInitializerImpl initializer =
                    new ProfileDataInitializerImpl(databaseManager, Runnable::run, ruleSet);

            initializer.initialize(profileManager(RecordingEventSource.PPROF),
                            CompletableFuture.completedFuture(null))
                    .get(5, TimeUnit.SECONDS);

            verify(threadManager, never()).threadRows();
            verify(autoAnalysisManager, never()).store(any());
            verify(databaseManager, never()).acquire(any());
            assertEquals(0, leasesTaken.get());
        }
    }
}
