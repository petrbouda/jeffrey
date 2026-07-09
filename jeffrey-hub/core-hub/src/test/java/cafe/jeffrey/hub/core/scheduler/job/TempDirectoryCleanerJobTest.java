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

package cafe.jeffrey.hub.core.scheduler.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.hub.core.scheduler.JobContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempDirectoryCleanerJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration RETENTION = Duration.ofHours(24);

    private static TempDirectoryCleanerJob job(Path tempDir) {
        return new TempDirectoryCleanerJob(tempDir, FIXED_CLOCK, Duration.ofHours(1), RETENTION);
    }

    private static void setModified(Path path, Instant instant) throws IOException {
        Files.setLastModifiedTime(path, FileTime.from(instant));
    }

    @Test
    void removesExpiredFilesAndDirectories_keepsFreshOnes(@TempDir Path tempDir) throws IOException {
        Path expiredFile = Files.createFile(tempDir.resolve("leaked-merge.jfr"));
        setModified(expiredFile, NOW.minus(Duration.ofDays(2)));

        Path expiredDir = Files.createDirectories(tempDir.resolve("replay-session-xyz"));
        Files.createFile(expiredDir.resolve("chunk.jfr"));
        setModified(expiredDir, NOW.minus(Duration.ofDays(3)));

        Path freshFile = Files.createFile(tempDir.resolve("in-flight-compression.lz4"));
        setModified(freshFile, NOW.minus(Duration.ofMinutes(10)));

        job(tempDir).execute(JobContext.EMPTY);

        assertFalse(Files.exists(expiredFile), "Expired file should be removed");
        assertFalse(Files.exists(expiredDir), "Expired directory should be removed recursively");
        assertTrue(Files.exists(freshFile), "Entry younger than the retention must stay (in-flight operation)");
    }

    @Test
    void keepsEntryExactlyAtRetentionBoundary(@TempDir Path tempDir) throws IOException {
        Path boundaryFile = Files.createFile(tempDir.resolve("boundary.tmp"));
        setModified(boundaryFile, NOW.minus(RETENTION));

        job(tempDir).execute(JobContext.EMPTY);

        assertTrue(Files.exists(boundaryFile), "Entry exactly at the cutoff is not strictly older — keep it");
    }

    @Test
    void toleratesMissingTempDirectory(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist");

        assertDoesNotThrow(() -> job(nonExistent).execute(JobContext.EMPTY));
    }
}
