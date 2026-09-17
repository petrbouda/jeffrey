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

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
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
 */
public class ProjectStorageQuotaCleanerJob extends RepositoryProjectJob {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectStorageQuotaCleanerJob.class);

    private static final String PARAM_MAX_SIZE = "max-size";

    private final Duration period;
    private final long budget;

    /**
     * @param config carries {@code max-size}, the per-project budget — {@code 20G}, {@code 512M},
     *               or plain bytes — which must be positive
     */
    public ProjectStorageQuotaCleanerJob(WorkspacesManager workspacesManager, JobConfig config) {
        super(workspacesManager);
        this.period = config.period();
        this.budget = config.bytesParam(PARAM_MAX_SIZE);
    }

    @Override
    protected void executeOnRepository(ProjectManager manager, RepositoryStorage repositoryStorage) {
        String projectName = manager.info().name();

        List<RecordingSession> sessions = repositoryStorage.listSessions(SessionDetail.WITH_FILES);
        long totalSize = sessions.stream()
                .mapToLong(RecordingSession::totalSizeBytes)
                .sum();

        if (totalSize <= budget) {
            return;
        }

        LOG.info("Project storage over budget, reclaiming oldest data: project_name={} total_bytes={} budget_bytes={}",
                projectName, totalSize, budget);

        long remaining = reclaimFinishedSessions(manager, sessions, totalSize, budget);
        if (remaining > budget) {
            remaining = trimLiveSessions(repositoryStorage, sessions, remaining, budget, projectName);
        }

        if (remaining > budget) {
            LOG.warn("Project still over storage budget after reclamation, remaining data is retained or in-flight: " +
                            "project_name={} total_bytes={} budget_bytes={}",
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
            // A session that was already gone freed nothing, so it must not count as reclaimed
            if (!manager.repositoryManager().deleteRecordingSession(session.id())) {
                continue;
            }
            projected -= session.totalSizeBytes();

            LOG.info("Deleted session to reclaim storage: project_name={} session_id={} freed_bytes={}",
                    manager.info().name(), session.id(), session.totalSizeBytes());
        }
        return projected;
    }

    /**
     * Trims closed chunks from the live sessions — one per live instance, oldest session first
     * — until the projected total fits the budget. The chunk a profiler is still writing is
     * never a candidate: {@link RecordingSession#finishedRecordings()} leaves it out.
     */
    private long trimLiveSessions(
            RepositoryStorage repositoryStorage,
            List<RecordingSession> sessions,
            long totalSize,
            long budget,
            String projectName) {

        List<RecordingSession> live = sessions.stream()
                .filter(session -> session.status() == RecordingStatus.ACTIVE)
                .filter(session -> !session.retained())
                .sorted(Comparator.comparing(RecordingSession::createdAt))
                .toList();

        long projected = totalSize;
        for (RecordingSession session : live) {
            if (projected <= budget) {
                break;
            }
            projected = trimSession(repositoryStorage, session, projected, budget, projectName);
        }
        return projected;
    }

    private long trimSession(
            RepositoryStorage repositoryStorage,
            RecordingSession session,
            long totalSize,
            long budget,
            String projectName) {

        List<String> toDelete = new ArrayList<>();
        long projected = totalSize;
        for (RepositoryFile file : session.finishedRecordings()) {
            if (projected <= budget) {
                break;
            }
            toDelete.add(file.id());
            projected -= file.size();
        }

        if (toDelete.isEmpty()) {
            return projected;
        }

        repositoryStorage.deleteRepositoryFiles(session.id(), toDelete);
        LOG.info("Trimmed recordings from live session to reclaim storage: project_name={} session_id={} count={}",
                projectName, session.id(), toDelete.size());

        return projected;
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
