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

package cafe.jeffrey.hub.core.scheduler.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempDirectoryCleanerJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration RETENTION = Duration.ofHours(24);

    private static TempDirectoryCleanerJob job(Path tempDir) {
        JobConfig config = new JobConfig(true, Duration.ofHours(1), Map.of("retention", "24h"));
        return new TempDirectoryCleanerJob(tempDir, FIXED_CLOCK, config);
    }

    private static void setModified(Path path, Instant instant) throws IOException {
        Files.setLastModifiedTime(path, FileTime.from(instant));
    }

    @Test
    void removesExpiredFilesAndDirectories_keepsFreshOnes(@TempDir Path tempDir) throws IOException {
        Path expiredFile = Files.createFile(tempDir.resolve("leaked-compression.jfr"));
        setModified(expiredFile, NOW.minus(Duration.ofDays(2)));

        Path expiredDir = Files.createDirectories(tempDir.resolve("replay-session-xyz"));
        Files.createFile(expiredDir.resolve("chunk.jfr"));
        setModified(expiredDir, NOW.minus(Duration.ofDays(3)));

        Path freshFile = Files.createFile(tempDir.resolve("in-flight-compression.lz4"));
        setModified(freshFile, NOW.minus(Duration.ofMinutes(10)));

        job(tempDir).execute();

        assertFalse(Files.exists(expiredFile), "Expired file should be removed");
        assertFalse(Files.exists(expiredDir), "Expired directory should be removed recursively");
        assertTrue(Files.exists(freshFile), "Entry younger than the retention must stay (in-flight operation)");
    }

    @Test
    void keepsEntryExactlyAtRetentionBoundary(@TempDir Path tempDir) throws IOException {
        Path boundaryFile = Files.createFile(tempDir.resolve("boundary.tmp"));
        setModified(boundaryFile, NOW.minus(RETENTION));

        job(tempDir).execute();

        assertTrue(Files.exists(boundaryFile), "Entry exactly at the cutoff is not strictly older — keep it");
    }

    @Test
    void toleratesMissingTempDirectory(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist");

        assertDoesNotThrow(() -> job(nonExistent).execute());
    }
}
