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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler job that compresses closed recording files using LZ4 compression.
 * <p>
 * Each tick compresses every session that is still recording — one per live instance of
 * the project, and a project may have several — and the newest finished one. Older finished
 * sessions were reached by an earlier tick and are skipped.
 * <p>
 * Within a session there is nothing further to decide: every closed chunk is compressed, and a
 * session that is still recording keeps the one chunk its profiler holds open out of it. Whether
 * a session is still recording is a fact of the session ({@code finishedAt} is null, until the
 * heartbeat, the reconciler or the expiry job stamps it), never of its position in the project's
 * listing; the job itself does not read a file to find out. Compressing the open chunk would
 * compress a prefix of it and then delete the file the profiler is writing into.
 */
public class RepositoryCompressionProjectJob extends RepositoryProjectJob {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryCompressionProjectJob.class);

    private final Duration period;

    public RepositoryCompressionProjectJob(WorkspacesManager workspacesManager, JobConfig config) {
        super(workspacesManager);
        this.period = config.period();
    }

    @Override
    protected void executeOnRepository(ProjectManager manager, RepositoryStorage repositoryStorage) {
        String projectName = manager.info().name();
        LOG.debug("Starting JFR compression check: project_name={}", projectName);

        // Headers only: compressSession loads the one session it works on with files itself
        List<RecordingSession> sessions = repositoryStorage.listSessions(SessionDetail.HEADERS);
        if (sessions.isEmpty()) {
            LOG.debug("No sessions found for compression: project_name={}", projectName);
            return;
        }

        List<RecordingSession> live = sessions.stream()
                .filter(session -> session.status() == RecordingStatus.ACTIVE)
                .toList();
        Optional<RecordingSession> newestFinished = sessions.stream()
                .filter(session -> session.status() == RecordingStatus.FINISHED)
                .max(Comparator.comparing(RecordingSession::createdAt));

        for (RecordingSession session : live) {
            repositoryStorage.compressSession(session.id());
        }
        newestFinished.ifPresent(session -> repositoryStorage.compressSession(session.id()));
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.REPOSITORY_JFR_COMPRESSION;
    }
}
