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

import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileDataInitializerImplTest {

    private static final String PROFILE_ID = "profile-1";

    private final ThreadManager threadManager = mock(ThreadManager.class);
    private final AutoAnalysisManager autoAnalysisManager = mock(AutoAnalysisManager.class);

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

    private ProfileManager profileManager(RecordingEventSource source) {
        ProfileInfo profileInfo = mock(ProfileInfo.class);
        when(profileInfo.id()).thenReturn(PROFILE_ID);
        when(profileInfo.eventSource()).thenReturn(source);

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.info()).thenReturn(profileInfo);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(profileManager.autoAnalysisManager()).thenReturn(autoAnalysisManager);
        return profileManager;
    }

    @BeforeEach
    void recordingIsOnDisk() {
        when(autoAnalysisManager.canGenerate()).thenReturn(true);
    }

    @Nested
    @DisplayName("Warming")
    class Warming {

        @Test
        @DisplayName("warms the cached views and releases the lease when they are done")
        void warmsTheViewsAndReleasesTheLease() throws Exception {
            ProfileDataInitializerImpl initializer =
                    new ProfileDataInitializerImpl(databaseManager(), Runnable::run);

            initializer.initialize(profileManager(RecordingEventSource.JDK))
                    .get(5, TimeUnit.SECONDS);

            verify(threadManager).threadRows();
            verify(autoAnalysisManager).generate();
            assertEquals(1, leasesTaken.get());
            assertTrue(leaseReleased.get(), "the warming lease was never released");
        }

        /**
         * The point of the change: the profile is usable the moment initialization returns, so the
         * warming must not still be running inside this call when it does.
         */
        @Test
        @DisplayName("returns before the warming has finished")
        void doesNotWaitForTheWarming() throws Exception {
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
                        new ProfileDataInitializerImpl(databaseManager(), executor);

                CompletableFuture<Void> warming =
                        initializer.initialize(profileManager(RecordingEventSource.JDK));

                assertTrue(warmStarted.await(2, TimeUnit.SECONDS));
                // initialize() has already returned while the warming is still parked.
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
         * not broken. It must not take the lease down with it.
         */
        @Test
        @DisplayName("a failing view does not strand the lease")
        void failureIsContained() throws Exception {
            when(threadManager.threadRows()).thenThrow(new IllegalStateException("warm blew up"));

            ProfileDataInitializerImpl initializer =
                    new ProfileDataInitializerImpl(databaseManager(), Runnable::run);

            initializer.initialize(profileManager(RecordingEventSource.JDK))
                    .get(5, TimeUnit.SECONDS);

            assertTrue(leaseReleased.get(), "a failed warm-up stranded the lease");
        }
    }

    @Nested
    @DisplayName("Auto analysis")
    class AutoAnalysis {

        /**
         * The rule set reads the original recording file, not the profile database. A profile whose
         * recording is gone cannot produce one, and saying so is not a warm-up failure.
         */
        @Test
        @DisplayName("is skipped when the recording file is gone, without failing the warm-up")
        void skippedWhenTheRecordingIsGone() throws Exception {
            when(autoAnalysisManager.canGenerate()).thenReturn(false);

            ProfileDataInitializerImpl initializer =
                    new ProfileDataInitializerImpl(databaseManager(), Runnable::run);

            initializer.initialize(profileManager(RecordingEventSource.JDK))
                    .get(5, TimeUnit.SECONDS);

            verify(autoAnalysisManager, never()).generate();
            verify(threadManager).threadRows();
            assertTrue(leaseReleased.get());
        }

        /**
         * The two warms are independent: the analysis is the expensive one and the likeliest to
         * blow up, and it must not take the thread bands or the lease with it.
         */
        @Test
        @DisplayName("a failing analysis still leaves the thread bands warmed and the lease released")
        void failureDoesNotAffectTheOtherWarm() throws Exception {
            when(autoAnalysisManager.generate()).thenThrow(new IllegalStateException("rules blew up"));

            ProfileDataInitializerImpl initializer =
                    new ProfileDataInitializerImpl(databaseManager(), Runnable::run);

            initializer.initialize(profileManager(RecordingEventSource.JDK))
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
                    new ProfileDataInitializerImpl(databaseManager, Runnable::run);

            initializer.initialize(profileManager(RecordingEventSource.PPROF))
                    .get(5, TimeUnit.SECONDS);

            verify(threadManager, never()).threadRows();
            verify(autoAnalysisManager, never()).generate();
            verify(databaseManager, never()).acquire(any());
            assertEquals(0, leasesTaken.get());
        }
    }
}
