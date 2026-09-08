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

package cafe.jeffrey.profile.heapdump.persistence;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staging directory's disk contract. {@code clearTable} is the half of it that spent a release
 * documented but never called, so what is pinned here is the behaviour the coordinators rely on:
 * clearing one table frees exactly that table, leaves the others alone, and is safe on a table that
 * produced no rows.
 */
class ParquetStagingTest {

    private static final String INSTANCE_TABLE = "instance";

    private static final String OUTBOUND_REF_TABLE = "outbound_ref";

    private static Path shard(ParquetStaging staging, String table, int partIdx) throws IOException {
        Path part = staging.partFile(table, partIdx);
        Files.writeString(part, "not really parquet, but it occupies a name and some bytes");
        return part;
    }

    @Nested
    class ClearTable {

        @Test
        void removesEveryShardOfTheClearedTable(@TempDir Path dir) throws IOException {
            try (ParquetStaging staging = ParquetStaging.open(dir.resolve("staging"))) {
                staging.prepareTable(INSTANCE_TABLE);
                Path first = shard(staging, INSTANCE_TABLE, 0);
                Path second = shard(staging, INSTANCE_TABLE, 1);

                staging.clearTable(INSTANCE_TABLE);

                assertFalse(Files.exists(first));
                assertFalse(Files.exists(second));
            }
        }

        /**
         * The point of clearing one table at a time: pass B loads three tables in sequence and the
         * heaviest is loaded last, so the ones already in the index DB have to go while the rest
         * are still needed.
         */
        @Test
        void leavesTheOtherTablesStanding(@TempDir Path dir) throws IOException {
            try (ParquetStaging staging = ParquetStaging.open(dir.resolve("staging"))) {
                staging.prepareTable(INSTANCE_TABLE);
                staging.prepareTable(OUTBOUND_REF_TABLE);
                Path instanceShard = shard(staging, INSTANCE_TABLE, 0);
                Path refShard = shard(staging, OUTBOUND_REF_TABLE, 0);

                staging.clearTable(INSTANCE_TABLE);

                assertFalse(Files.exists(instanceShard));
                assertTrue(Files.exists(refShard));
            }
        }

        /**
         * A phase that produced zero rows leaves {@code bulkLoad} a no-op and the directory
         * possibly absent; the clear that follows it unconditionally must not turn that into a
         * failed index build.
         */
        @Test
        void isANoOpForATableThatWasNeverPrepared(@TempDir Path dir) throws IOException {
            try (ParquetStaging staging = ParquetStaging.open(dir.resolve("staging"))) {
                staging.clearTable(INSTANCE_TABLE);
            }
        }

        @Test
        void isANoOpForAPreparedTableWithNoShards(@TempDir Path dir) throws IOException {
            try (ParquetStaging staging = ParquetStaging.open(dir.resolve("staging"))) {
                staging.prepareTable(INSTANCE_TABLE);

                staging.clearTable(INSTANCE_TABLE);
                staging.clearTable(INSTANCE_TABLE);
            }
        }

        /**
         * Clearing is a disk reclaim, not a teardown: the phase carries on writing after it, so the
         * staging root has to survive and stay usable.
         */
        @Test
        void leavesTheStagingRootUsable(@TempDir Path dir) throws IOException {
            try (ParquetStaging staging = ParquetStaging.open(dir.resolve("staging"))) {
                staging.prepareTable(INSTANCE_TABLE);
                shard(staging, INSTANCE_TABLE, 0);
                staging.clearTable(INSTANCE_TABLE);

                assertTrue(Files.isDirectory(staging.directory()));

                staging.prepareTable(OUTBOUND_REF_TABLE);
                assertTrue(Files.exists(shard(staging, OUTBOUND_REF_TABLE, 0)));
            }
        }
    }

    @Nested
    class Close {

        @Test
        void removesTheWholeStagingDirectoryEvenWhenTablesWereNeverCleared(@TempDir Path dir)
                throws IOException {
            Path stagingDir = dir.resolve("staging");
            try (ParquetStaging staging = ParquetStaging.open(stagingDir)) {
                staging.prepareTable(INSTANCE_TABLE);
                shard(staging, INSTANCE_TABLE, 0);
            }

            assertFalse(Files.exists(stagingDir));
        }
    }
}
