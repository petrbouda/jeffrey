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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sweeps the hub's temp directory. Scratch files (JFR merges, compression staging,
 * replay windows) are normally removed by their owners, but a crash between creating
 * and deleting them leaks the entry — without this job it would live until the next
 * process restart. Entries older than the retention window are removed; anything
 * younger is presumed to belong to an in-flight operation.
 */
public class TempDirectoryCleanerJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(TempDirectoryCleanerJob.class);

    private static final String PARAM_RETENTION = "retention";

    private final Path tempDir;
    private final Clock clock;
    private final Duration period;
    private final Duration retention;

    public TempDirectoryCleanerJob(Path tempDir, Clock clock, JobConfig config) {
        this.tempDir = tempDir;
        this.clock = clock;
        this.period = config.period();
        this.retention = config.durationParam(PARAM_RETENTION);
    }

    @Override
    public void execute(JobContext context) {
        if (!FileSystemUtils.isDirectory(tempDir)) {
            return;
        }

        Instant cutoff = clock.instant().minus(retention);
        List<Path> entries;
        try (Stream<Path> stream = Files.list(tempDir)) {
            entries = stream.toList();
        } catch (IOException e) {
            LOG.warn("Cannot list temp directory for cleanup: temp_dir={}", tempDir, e);
            return;
        }

        int removed = 0;
        for (Path entry : entries) {
            if (removeIfExpired(entry, cutoff)) {
                removed++;
            }
        }

        if (removed > 0) {
            LOG.info("Removed leaked temp entries: temp_dir={} count={} retention={}", tempDir, removed, retention);
        }
    }

    private boolean removeIfExpired(Path entry, Instant cutoff) {
        try {
            Instant modifiedAt = FileSystemUtils.modifiedAt(entry);
            if (!modifiedAt.isBefore(cutoff)) {
                return false;
            }
            if (Files.isDirectory(entry)) {
                FileSystemUtils.removeDirectory(entry);
            } else {
                FileSystemUtils.removeFile(entry);
            }
            LOG.debug("Removed expired temp entry: path={} modified_at={}", entry, modifiedAt);
            return true;
        } catch (Exception e) {
            // Best-effort: the entry may be concurrently removed by its owner
            LOG.debug("Could not remove temp entry: path={}", entry, e);
            return false;
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.TEMP_DIRECTORY_CLEANER;
    }
}
