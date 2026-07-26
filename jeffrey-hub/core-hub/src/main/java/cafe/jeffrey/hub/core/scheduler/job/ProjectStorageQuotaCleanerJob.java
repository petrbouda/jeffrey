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
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectStorageQuotaCleanerJobDescriptor;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bounds how much disk a single project's repository may occupy.
 *
 * <p>The age-based cleaners answer "how long do we keep data"; they cannot answer
 * "how much disk may this project consume". A service producing several gigabytes an
 * hour fills the volume long before anything reaches the session or recording retention
 * window, so this job reclaims by size instead of by age.</p>
 *
 * <p>Reclamation is oldest-first and runs in two stages, mirroring the granularity split
 * the age-based cleaners already use:</p>
 * <ol>
 *   <li>whole finished sessions, oldest first — the coarse unit;</li>
 *   <li>if still over budget, finished chunks inside the live session, oldest first.</li>
 * </ol>
 *
 * <p>Retained sessions and the chunk the profiler is currently writing are never
 * touched, so a project pinned entirely to retention may legitimately stay over
 * budget; that is reported as a warning rather than forced.</p>
 *
 * <p>Session deletion is published as a workspace event and applied asynchronously, so
 * the freed bytes are subtracted optimistically as deletions are requested. A tick that
 * runs while a previous batch is still draining would see those sessions again — in
 * practice the synchronizer drains far more often than this job runs, and a queue stalled
 * long enough to matter is a larger problem than the quota overshoot it could cause.</p>
 */
public class ProjectStorageQuotaCleanerJob extends RepositoryProjectJob<ProjectStorageQuotaCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectStorageQuotaCleanerJob.class);

    private final Duration period;

    public ProjectStorageQuotaCleanerJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            ProjectStorageQuotaCleanerJobDescriptor jobDescriptor,
            Duration period) {

        super(workspacesManager, repositoryStorageFactory, jobDescriptor);
        this.period = period;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RepositoryStorage repositoryStorage,
            ProjectStorageQuotaCleanerJobDescriptor jobDescriptor,
            JobContext context) {

        String projectName = manager.info().name();
        long budget = jobDescriptor.maxSizeBytes();

        List<RecordingSession> sessions = repositoryStorage.listSessions(true);
        long totalSize = sessions.stream()
                .mapToLong(RecordingSession::totalSizeBytes)
                .sum();

        if (totalSize <= budget) {
            return;
        }

        LOG.info("Project storage over budget, reclaiming oldest data: project='{}' total_bytes={} budget_bytes={}",
                projectName, totalSize, budget);

        long remaining = reclaimFinishedSessions(manager, sessions, totalSize, budget);
        if (remaining > budget) {
            remaining = trimActiveSession(repositoryStorage, sessions, remaining, budget, projectName);
        }

        if (remaining > budget) {
            LOG.warn("Project still over storage budget after reclamation, remaining data is retained or in-flight: " +
                            "project='{}' total_bytes={} budget_bytes={}",
                    projectName, remaining, budget);
        }
    }

    /**
     * Deletes whole finished, non-retained sessions oldest-first until the projected
     * total fits the budget. Returns the projected total after the requested deletions.
     */
    private long reclaimFinishedSessions(
            ProjectManager manager, List<RecordingSession> sessions, long totalSize, long budget) {

        List<RecordingSession> deletable = sessions.stream()
                .filter(session -> session.finishedAt() != null)
                .filter(session -> !session.retained())
                .sorted(Comparator.comparing(RecordingSession::createdAt))
                .toList();

        long projected = totalSize;
        for (RecordingSession session : deletable) {
            if (projected <= budget) {
                break;
            }
            manager.repositoryManager()
                    .deleteRecordingSession(session.id(), WorkspaceEventCreator.PROJECT_STORAGE_QUOTA_CLEANER_JOB);
            projected -= session.totalSizeBytes();

            LOG.info("Deleted session to reclaim storage: project='{}' session={} freed_bytes={}",
                    manager.info().name(), session.id(), session.totalSizeBytes());
        }
        return projected;
    }

    /**
     * Trims finished chunks from the live session oldest-first. The chunk the profiler is
     * still writing carries a non-FINISHED status and is therefore never a candidate.
     */
    private long trimActiveSession(
            RepositoryStorage repositoryStorage,
            List<RecordingSession> sessions,
            long totalSize,
            long budget,
            String projectName) {

        RecordingSession active = sessions.stream()
                .filter(session -> session.status() == RecordingStatus.ACTIVE)
                .filter(session -> !session.retained())
                .max(Comparator.comparing(RecordingSession::createdAt))
                .orElse(null);

        if (active == null) {
            return totalSize;
        }

        List<RepositoryFile> trimmable = active.files().stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(RepositoryFile::isFinished)
                .sorted(Comparator.comparing(
                        RepositoryFile::createdAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        List<String> toDelete = new ArrayList<>();
        long projected = totalSize;
        for (RepositoryFile file : trimmable) {
            if (projected <= budget) {
                break;
            }
            toDelete.add(file.id());
            projected -= fileSize(file);
        }

        if (toDelete.isEmpty()) {
            return projected;
        }

        repositoryStorage.deleteRepositoryFiles(active.id(), toDelete);
        LOG.info("Trimmed recordings from active session to reclaim storage: project='{}' session={} count={}",
                projectName, active.id(), toDelete.size());

        return projected;
    }

    private static long fileSize(RepositoryFile file) {
        return file.size() != null ? file.size() : 0L;
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECT_STORAGE_QUOTA_CLEANER;
    }
}
