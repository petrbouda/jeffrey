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

import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.OrphanedSessionCleanerJobDescriptor;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reclaims session directories that exist on disk but have no database row.
 *
 * <p>Nothing else reconciles the filesystem against the database: every other path
 * resolves directories <em>from</em> session rows and so is structurally blind to a
 * directory the database has lost track of. Such directories accumulate when a session
 * is written but its creation event never lands — a workspace event dropped after
 * exhausting its retry budget, or a crash between creating the directory and publishing
 * the event — and without this job they persist for the lifetime of the volume.</p>
 *
 * <p>A directory is only removed once it is older than the configured grace period. A
 * synchronizer backlog is indistinguishable from an orphan at a single point in time,
 * so the window has to be comfortably longer than the queue's worst-case drain.</p>
 *
 * <p>Scope is deliberately one project at a time: the sweep only ever descends into the
 * repository path of a project that still exists. Directories belonging to a deleted
 * project or workspace are therefore out of reach — those are cleaned eagerly at delete
 * time instead.</p>
 */
public class OrphanedSessionCleanerJob extends RepositoryProjectJob<OrphanedSessionCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(OrphanedSessionCleanerJob.class);

    private final Duration period;
    private final Clock clock;

    public OrphanedSessionCleanerJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            OrphanedSessionCleanerJobDescriptor jobDescriptor,
            Duration period,
            Clock clock) {

        super(workspacesManager, repositoryStorageFactory, jobDescriptor);
        this.period = period;
        this.clock = clock;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RepositoryStorage repositoryStorage,
            OrphanedSessionCleanerJobDescriptor jobDescriptor,
            JobContext context) {

        List<Path> onDisk = repositoryStorage.listSessionDirectoriesOnDisk();
        if (onDisk.isEmpty()) {
            return;
        }

        Set<Path> known = new HashSet<>();
        for (RecordingSession session : repositoryStorage.listSessions(false)) {
            known.add(session.absolutePath());
        }

        Instant cutoff = clock.instant().minus(jobDescriptor.toDuration());
        String projectName = manager.info().name();

        int removed = 0;
        for (Path candidate : onDisk) {
            if (known.contains(candidate)) {
                continue;
            }
            if (removeIfSettled(candidate, cutoff, projectName)) {
                removed++;
            }
        }

        if (removed > 0) {
            LOG.info("Removed orphaned session directories: project='{}' count={} grace={}",
                    projectName, removed, jobDescriptor.toDuration());
        }
    }

    private boolean removeIfSettled(Path candidate, Instant cutoff, String projectName) {
        try {
            Instant modifiedAt = FileSystemUtils.modifiedAt(candidate);
            if (!modifiedAt.isBefore(cutoff)) {
                return false;
            }
            FileSystemUtils.removeDirectory(candidate);
            LOG.info("Removed orphaned session directory: project='{}' path={} modified_at={}",
                    projectName, candidate, modifiedAt);
            return true;
        } catch (Exception e) {
            // Best-effort: the directory may be claimed by a late-arriving event mid-sweep
            LOG.warn("Could not remove orphaned session directory: project='{}' path={}", projectName, candidate, e);
            return false;
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.ORPHANED_SESSION_CLEANER;
    }
}
