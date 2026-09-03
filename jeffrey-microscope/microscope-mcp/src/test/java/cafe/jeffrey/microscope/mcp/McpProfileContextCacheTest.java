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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
    McpProfileResolver profileResolver;

    @Mock
    DatabaseManagerResolver databaseManagerResolver;

    private McpProfileContextCache newCache() {
        return new McpProfileContextCache(
                profileResolver, databaseManagerResolver, clock, IDLE_TIMEOUT, false);
    }

    private ProfileManager stubProfile(String profileId) {
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.info()).thenReturn(new ProfileInfo(
                profileId, "proj", "ws", "Profile", RecordingEventSource.JDK,
                start, start.plusSeconds(60), start, true, false, "rec-1"));
        when(profileResolver.resolve(profileId)).thenReturn(profileManager);
        when(databaseManagerResolver.acquire(profileManager.info())).thenAnswer(invocation ->
                new DatabaseLease(mock(DataSource.class), () -> released.add(profileId)));
        return profileManager;
    }

    @Nested
    class Caching {

        @Test
        void opensAProfileOnFirstUse() {
            ProfileManager profileManager = stubProfile(PROFILE);

            assertSame(profileManager, newCache().profileManager(PROFILE));
        }

        /**
         * The lease is the point of the cache: acquiring one per call would defeat it, because the
         * pool would be free to be idle-evicted between the model's questions.
         */
        @Test
        void reusesTheSameLeaseAcrossCalls() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();

            cache.profileManager(PROFILE);
            cache.profileManager(PROFILE);

            assertEquals(1, cache.size());
            assertTrue(released.isEmpty());
        }

        @Test
        void holdsOneContextPerProfile() {
            stubProfile("p-1");
            stubProfile("p-2");
            McpProfileContextCache cache = newCache();

            cache.profileManager("p-1");
            cache.profileManager("p-2");

            assertEquals(2, cache.size());
        }

        /**
         * A client issues several tool calls at once. Two landing on the same new profile must share
         * one lease, or the second would leak the pool it pinned.
         */
        @Test
        void acquiresOnceUnderConcurrentFirstUse() throws Exception {
            stubProfile(PROFILE);
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
                        return cache.profileManager(PROFILE);
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
        }
    }

    @Nested
    class Eviction {

        @Test
        void releasesTheLeaseOfAnIdleProfile() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.profileManager(PROFILE);

            clock.advance(IDLE_TIMEOUT.plusMinutes(1));
            cache.evictIdle();

            assertEquals(List.of(PROFILE), released);
            assertEquals(0, cache.size());
        }

        @Test
        void keepsAProfileThatIsStillBeingAskedAbout() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.profileManager(PROFILE);

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
            cache.profileManager(PROFILE);

            clock.advance(IDLE_TIMEOUT.minusMinutes(1));
            cache.profileManager(PROFILE);
            clock.advance(IDLE_TIMEOUT.minusMinutes(1));
            cache.evictIdle();

            assertTrue(released.isEmpty());
        }

        @Test
        void evictsOnDemand() {
            stubProfile(PROFILE);
            McpProfileContextCache cache = newCache();
            cache.profileManager(PROFILE);

            cache.evict(PROFILE);

            assertEquals(List.of(PROFILE), released);
        }

        @Test
        void ignoresEvictingAProfileItNeverOpened() {
            newCache().evict("never-opened");

            assertTrue(released.isEmpty());
        }

        @Test
        void releasesEverythingOnClose() {
            stubProfile("p-1");
            stubProfile("p-2");
            McpProfileContextCache cache = newCache();
            cache.profileManager("p-1");
            cache.profileManager("p-2");

            cache.close();

            assertEquals(2, released.size());
            assertEquals(0, cache.size());
        }
    }

    /**
     * A clock the test moves by hand, so idle eviction is asserted rather than waited for.
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
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
            return now;
        }
    }
}
