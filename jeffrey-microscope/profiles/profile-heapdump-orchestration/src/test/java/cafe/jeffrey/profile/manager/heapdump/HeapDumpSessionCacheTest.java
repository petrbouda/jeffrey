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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeapDumpSessionCacheTest {

    private static final Instant START = Instant.ofEpochMilli(1_000_000L);

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);

    /** Mutable, manually advanced clock so idle eviction is deterministic. */
    private static final class MutableClock extends Clock {

        private Instant now = START;

        @Override
        public ZoneId getZone() {
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

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @TempDir
    Path tempDir;

    private final MutableClock clock = new MutableClock();

    private final HeapDumpSessionCache cache = new HeapDumpSessionCache(clock, IDLE_TIMEOUT);

    @AfterEach
    void closeCache() {
        cache.close();
    }

    private Path writeSyntheticDump(String fileName) throws IOException {
        return SyntheticHeapDumps.writeMinimalDump(tempDir, fileName);
    }

    private HeapDumpSession leaseSession(Path hprof) throws IOException, SQLException {
        return cache.withSession(hprof, session -> session);
    }

    @Nested
    class Reuse {

        @Test
        void reusesSessionAcrossWorkUnits() throws IOException, SQLException {
            Path hprof = writeSyntheticDump("reuse.hprof");

            HeapDumpSession first = leaseSession(hprof);
            HeapDumpSession second = leaseSession(hprof);

            assertSame(first, second, "second work unit must reuse the cached session");
        }

        @Test
        void sessionStaysUsableAcrossWorkUnits() throws IOException, SQLException {
            Path hprof = writeSyntheticDump("usable.hprof");

            leaseSession(hprof);
            long recordCount = cache.withSession(hprof,
                    session -> session.view().metadata().recordCount());

            assertTrue(recordCount > 0, "cached session must serve queries");
        }
    }

    @Nested
    class Freshness {

        @Test
        void reopensWhenDumpMtimeChanges() throws IOException, SQLException {
            Path hprof = writeSyntheticDump("mtime.hprof");

            HeapDumpSession first = leaseSession(hprof);
            Files.setLastModifiedTime(hprof, FileTime.from(
                    Files.getLastModifiedTime(hprof).toInstant().plusSeconds(60)));
            HeapDumpSession second = leaseSession(hprof);

            assertNotSame(first, second, "mtime change must evict the cached session");
        }

        @Test
        void reopensWhenIndexIsDeletedUnderneath() throws IOException, SQLException {
            Path hprof = writeSyntheticDump("index-gone.hprof");

            HeapDumpSession first = leaseSession(hprof);
            // Deleted without invalidate() — the freshness check is the safety
            // net for callers that bypass the lifecycle hooks.
            Files.deleteIfExists(HeapDumpIndexPaths.indexFor(hprof));
            HeapDumpSession second = leaseSession(hprof);

            assertNotSame(first, second);
            assertTrue(Files.exists(HeapDumpIndexPaths.indexFor(hprof)), "index must be rebuilt");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void invalidateForcesReopen() throws IOException, SQLException {
            Path hprof = writeSyntheticDump("invalidate.hprof");

            HeapDumpSession first = leaseSession(hprof);
            cache.invalidate(hprof);
            HeapDumpSession second = leaseSession(hprof);

            assertNotSame(first, second);
        }

        @Test
        void evictIdleClosesSessionAfterTimeout() throws IOException, SQLException {
            Path hprof = writeSyntheticDump("idle.hprof");

            HeapDumpSession first = leaseSession(hprof);
            clock.advance(IDLE_TIMEOUT.plusSeconds(1));
            cache.evictIdle();
            HeapDumpSession second = leaseSession(hprof);

            assertNotSame(first, second, "idle session must be evicted after the timeout");
        }

        @Test
        void evictIdleKeepsRecentlyUsedSession() throws IOException, SQLException {
            Path hprof = writeSyntheticDump("recent.hprof");

            HeapDumpSession first = leaseSession(hprof);
            clock.advance(IDLE_TIMEOUT.minusSeconds(1));
            cache.evictIdle();
            HeapDumpSession second = leaseSession(hprof);

            assertSame(first, second, "recently used session must survive the sweep");
        }
    }

}
