/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpProfileContextCacheTest {

    private static final String PROFILE = "p-1";
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");
    private final MutableClock clock = new MutableClock(start);

    private final List<String> released = new ArrayList<>();

    @Mock
    ProfileManagerResolver profileManagerResolver;

    @Mock
    DatabaseManagerResolver databaseManagerResolver;

    private McpProfileContextCache newCache() {
        return new McpProfileContextCache(
                profileManagerResolver, databaseManagerResolver, clock, IDLE_TIMEOUT, false);
    }

    private ProfileManager stubProfile(String profileId) {
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.info()).thenReturn(new ProfileInfo(
                profileId, "proj", "ws", "Profile", RecordingEventSource.JDK,
                start, start.plusSeconds(60), start, true, false, "rec-1"));
        when(profileManagerResolver.resolve(profileId)).thenReturn(profileManager);
        when(databaseManagerResolver.acquire(profileManager.info())).thenAnswer(invocation ->
                new DatabaseLease(mock(DataSource.class), () -> released.add(profileId)));
        return profileManager;
    }

    private void stubProfileWhoseReleaseFails(String profileId) {
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.info()).thenReturn(new ProfileInfo(
                profileId, "proj", "ws", "Profile", RecordingEventSource.JDK,
                start, start.plusSeconds(60), start, true, false, "rec-1"));
        when(profileManagerResolver.resolve(profileId)).thenReturn(profileManager);
        when(databaseManagerResolver.acquire(profileManager.info())).thenAnswer(invocation ->
                new DatabaseLease(mock(DataSource.class), () -> {
                    throw new IllegalStateException("pool already closed");
                }));
    }

    @Nested
    class Caching {

        @Test
        void opensAProfileOnFirstUse() {
            ProfileManager profileManager = stubProfile(PROFILE);

            try (McpProfileContextCache.Lease lease = newCache().acquire(PROFILE)) {
                assertSame(profileManager, lease.profileManager());
            }
        }

        /**
         * The lease is the point of the cache: acquiring one per call would defeat it, because the
         * pool would be free to be idle-evicted between the model's questions.
         */
        @Test
        void reusesTheSameLeaseAcrossCalls() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();

            try (McpProfileContextCache.Lease ignored = cache.acquire(PROFILE)) {
                // The context remains cached after this call lease is released.
            }
            try (McpProfileContextCache.Lease ignored = cache.acquire(PROFILE)) {
                // A later call renews the same cached context.
            }

            assertEquals(1, cache.size());
            assertTrue(released.isEmpty());
        }

        @Test
        void holdsOneContextPerProfile() {
            stubProfile("p-1");
            stubProfile("p-2");
            McpProfileContextCache cache = newCache();

            try (McpProfileContextCache.Lease ignored = cache.acquire("p-1")) {
                // Open the first cached context.
            }
            try (McpProfileContextCache.Lease ignored = cache.acquire("p-2")) {
                // Open the second cached context.
            }

            assertEquals(2, cache.size());
        }

        /**
         * A client issues several tool calls at once. Two landing on the same new profile must share
         * one lease, or the second would leak the pool it pinned.
         */
        @Test
        void acquiresOnceUnderConcurrentFirstUse() throws Exception {
            ProfileManager profileManager = stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();

            int threads = 8;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                for (int i = 0; i < threads; i++) {
                    pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try (McpProfileContextCache.Lease lease = cache.acquire(PROFILE)) {
                            return lease.profileManager();
                        }
                    });
                }
                ready.await(5, TimeUnit.SECONDS);
                go.countDown();
                pool.shutdown();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }

            assertEquals(1, cache.size());
            verify(databaseManagerResolver).acquire(profileManager.info());
        }
    }

    @Nested
    class Eviction {

        @Test
        void releasesTheLeaseOfAnIdleProfile() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.acquire(PROFILE).close();

            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            cache.evictIdle();

            assertEquals(List.of(PROFILE), released);
            assertEquals(0, cache.size());
        }

        @Test
        void keepsAProfileThatIsStillBeingAskedAbout() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.acquire(PROFILE).close();

            clock.advance(IDLE_TIMEOUT.minusMinutes(1));
            cache.evictIdle();

            assertTrue(released.isEmpty());
            assertEquals(1, cache.size());
        }

        /**
         * Every call is a use. Without this a long session would be evicted mid-analysis, exactly
         * thirty minutes after its first question rather than after its last.
         */
        @Test
        void aCallRefreshesTheIdleWindow() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.acquire(PROFILE).close();

            clock.advance(IDLE_TIMEOUT.minusMinutes(1));
            cache.acquire(PROFILE).close();
            clock.advance(IDLE_TIMEOUT.minusMinutes(1));
            cache.evictIdle();

            assertTrue(released.isEmpty());

            clock.advance(Duration.ofMinutes(2));
            cache.evictIdle();
            assertEquals(List.of(PROFILE), released);
        }

        @Test
        void evictsOnDemand() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.acquire(PROFILE).close();

            cache.invalidate(PROFILE);

            assertEquals(List.of(PROFILE), released);
        }

        @Test
        void ignoresEvictingAProfileItNeverOpened() {
            newCache().invalidate("never-opened");

            assertTrue(released.isEmpty());
        }

        @Test
        void releasesEverythingOnClose() {
            stubProfile("p-1");
            stubProfile("p-2");
            McpProfileContextCache cache = newCache();
            cache.acquire("p-1").close();
            cache.acquire("p-2").close();

            cache.close();

            assertEquals(2, released.size());
            assertEquals(0, cache.size());
        }

        @Test
        void idleSweepDoesNotCloseAnActiveCall() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            McpProfileContextCache.Lease active = cache.acquire(PROFILE);

            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            cache.evictIdle();

            assertTrue(released.isEmpty());
            assertEquals(1, cache.size());

            active.close();
            cache.evictIdle();
            assertTrue(released.isEmpty(), "release renews the idle window from call completion");

            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            cache.evictIdle();
            assertEquals(List.of(PROFILE), released);
        }

        /**
         * A pool that refuses to close is that profile's problem, not the sweep's. One release
         * throwing used to abandon the rest of the sweep -- and, run from the scheduler, every sweep
         * after it, so nothing was ever evicted again.
         */
        @Test
        void aFailingReleaseDoesNotStopTheSweepFromEvictingTheOthers() {
            stubProfile("p-1");
            stubProfileWhoseReleaseFails("p-2");
            stubProfile("p-3");
            McpProfileContextCache cache = newCache();
            cache.acquire("p-1").close();
            cache.acquire("p-2").close();
            cache.acquire("p-3").close();

            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            assertDoesNotThrow(cache::evictIdle);

            assertEquals(Set.of("p-1", "p-3"), Set.copyOf(released));
            assertEquals(0, cache.size(), "the context whose release failed is closed and dropped too");
        }

        @Test
        void aSweepThatFailsDoesNotPreventTheNextOne() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.acquire(PROFILE).close();

            clock.failNextRead();
            assertDoesNotThrow(cache::sweep);
            assertTrue(released.isEmpty());

            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            cache.sweep();
            assertEquals(List.of(PROFILE), released);
        }

        @Test
        void invalidationDefersOneCloseUntilTheActiveCallEnds() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            McpProfileContextCache.Lease active = cache.acquire(PROFILE);

            cache.invalidate(PROFILE);
            cache.invalidate(PROFILE);

            assertTrue(released.isEmpty());
            assertEquals(0, cache.size());

            active.close();
            active.close();
            assertEquals(List.of(PROFILE), released);
        }

        @Test
        void closingTheCachePreventsAConcurrentCallFromResurrectingAContext() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            McpProfileContextCache.Lease active = cache.acquire(PROFILE);

            cache.close();

            assertEquals(0, cache.size());
            assertThrows(IllegalStateException.class, () -> cache.acquire(PROFILE));
            assertTrue(released.isEmpty());

            active.close();
            assertEquals(List.of(PROFILE), released);
        }

        @Test
        void closingWhileAProfileIsBeingResolvedClosesTheCandidateAndRefusesTheCall() throws Exception {
            ProfileManager profileManager = mock(ProfileManager.class);
            when(profileManager.info()).thenReturn(new ProfileInfo(
                    PROFILE, "proj", "ws", "Profile", RecordingEventSource.JDK,
                    start, start.plusSeconds(60), start, true, false, "rec-1"));
            CountDownLatch resolving = new CountDownLatch(1);
            CountDownLatch continueResolving = new CountDownLatch(1);
            when(profileManagerResolver.resolve(PROFILE)).thenAnswer(invocation -> {
                resolving.countDown();
                assertTrue(continueResolving.await(5, TimeUnit.SECONDS));
                return profileManager;
            });
            when(databaseManagerResolver.acquire(profileManager.info())).thenReturn(
                    new DatabaseLease(mock(DataSource.class), () -> released.add(PROFILE)));
            McpProfileContextCache cache = newCache();
            ExecutorService caller = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> refused = caller.submit(() -> {
                    try (McpProfileContextCache.Lease ignored = cache.acquire(PROFILE)) {
                        return false;
                    } catch (IllegalStateException expected) {
                        return true;
                    }
                });

                assertTrue(resolving.await(5, TimeUnit.SECONDS));
                cache.close();
                continueResolving.countDown();

                assertTrue(refused.get(5, TimeUnit.SECONDS));
            } finally {
                continueResolving.countDown();
                caller.shutdownNow();
            }

            assertEquals(0, cache.size());
            assertEquals(List.of(PROFILE), released);
        }
    }

    /**
     * A clock the test moves by hand, so idle eviction is asserted rather than waited for.
     */
    private static final class MutableClock extends Clock {

        private Instant now;
        private boolean failNextRead;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        /** The next read throws, which is the cheapest way to make a whole sweep fail. */
        void failNextRead() {
            failNextRead = true;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            if (failNextRead) {
                failNextRead = false;
                throw new IllegalStateException("clock unavailable");
            }
            return now;
        }
    }
}
