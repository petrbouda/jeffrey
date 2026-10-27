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

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduler job that compresses closed recording files using LZ4 compression.
 * <p>
 * Each tick compresses, per instance of the project — and a project may have several — its two
 * newest sessions: the newest, which keeps recording, and the one before it, whose last recording
 * became complete when the newest started. Older sessions were reached by an earlier tick and are
 * skipped.
 * <p>
 * Within a session there is nothing further to decide: every closed chunk is compressed, and the
 * instance's newest session keeps its newest recording out of it — the profiler writes each
 * recording whole when its loop ends, so that one may still be on its way onto the volume. Which
 * session is the newest is a fact of the instance (its sessions' order), never of the session's
 * position in the project's listing. Compressing a recording still being written would compress a
 * prefix of it and then delete the file the profiler is writing into.
 */
public class RepositoryCompressionProjectJob extends RepositoryProjectJob {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryCompressionProjectJob.class);

    /** The newest session and the one it ended: the only sessions a tick can find anything new in. */
    private static final int SESSIONS_PER_INSTANCE = 2;

    private static final Comparator<RecordingSession> NEWEST_FIRST =
            Comparator.comparing(RecordingSession::createdAt).reversed();

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

        Collection<List<RecordingSession>> byInstance = sessions.stream()
                .collect(Collectors.groupingBy(RecordingSession::instanceId))
                .values();
        for (List<RecordingSession> instanceSessions : byInstance) {
            instanceSessions.stream()
                    .sorted(NEWEST_FIRST)
                    .limit(SESSIONS_PER_INSTANCE)
                    .forEach(session -> repositoryStorage.compressSession(session.id()));
        }
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
