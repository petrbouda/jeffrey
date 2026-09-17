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

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.session.SessionFinisher;
import cafe.jeffrey.hub.core.project.session.SessionPaths;
import cafe.jeffrey.hub.core.project.session.SessionRef;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.job.JobType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Scheduler job that detects when sessions become FINISHED and emits SESSION_FINISHED events.
 * <p>
 * This job periodically checks all sessions that have not been marked as finished yet.
 * For each unfinished session, it uses {@link SessionFinisher} to determine if the session
 * has actually finished based on heartbeat data. When a session is detected as finished, it
 * also checks if the parent instance should be auto-finished.
 * <p>
 * Only sessions that promised to report liveness are examined here; the rest are left alone and
 * closed by the reconciler when the instance's next session appears.
 */
public class SessionFinishedDetectorProjectJob extends RepositoryProjectJob {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinishedDetectorProjectJob.class);

    /**
     * How long after its last heartbeat a session that promised to report is taken as ended.
     */
    private static final String PARAM_HEARTBEAT_THRESHOLD = "heartbeat-threshold";

    private final Duration period;
    private final Duration heartbeatThreshold;
    private final HubJeffreyDirs jeffreyDirs;
    private final HubPlatformRepositories platformRepositories;
    private final SessionFinisher sessionFinisher;

    public SessionFinishedDetectorProjectJob(
            WorkspacesManager workspacesManager,
            JobConfig config,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher) {

        super(workspacesManager);
        this.period = config.period();
        this.heartbeatThreshold = config.durationParam(PARAM_HEARTBEAT_THRESHOLD);
        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.sessionFinisher = sessionFinisher;
    }

    @Override
    protected void executeOnRepository(ProjectManager manager, RepositoryStorage repositoryStorage) {
        ProjectInfo projectInfo = manager.info();
        ProjectRepositoryRepository projectRepositoryRepository =
                platformRepositories.newProjectRepositoryRepository(projectInfo.id());

        List<ProjectInstanceSessionInfo> unfinishedSessions = projectRepositoryRepository.findUnfinishedSessions();
        if (unfinishedSessions.isEmpty()) {
            return;
        }

        // A session belongs to a repository, so one exists; the storage has it cached
        RepositoryInfo repositoryInfo = repositoryStorage.repositoryInfo();

        for (ProjectInstanceSessionInfo sessionInfo : unfinishedSessions) {
            Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, sessionInfo);

            sessionFinisher.tryFinishFromHeartbeat(new SessionRef(projectInfo, sessionInfo, sessionPath), heartbeatThreshold);
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.SESSION_FINISHED_DETECTOR;
    }
}
