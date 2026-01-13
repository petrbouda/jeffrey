/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.project.repository.detection.StatusStrategy;
import pbouda.jeffrey.platform.project.repository.detection.WithDetectionFileStrategy;
import pbouda.jeffrey.platform.project.repository.detection.WithoutDetectionFileStrategy;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.SessionFinishedDetectorProjectJobDescriptor;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.workspace.RepositorySessionInfo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Scheduler job that detects when sessions become FINISHED and emits SESSION_FINISHED events.
 * <p>
 * This job periodically checks all sessions that have not been marked as finished yet.
 * For each unfinished session, it uses the StatusStrategy to determine if the session
 * has actually finished (either by detection file or timeout). When a session is detected
 * as finished, it:
 * 1. Updates the finished_at timestamp in the database
 * 2. Emits a SESSION_FINISHED workspace event
 */
public class SessionFinishedDetectorProjectJob extends RepositoryProjectJob<SessionFinishedDetectorProjectJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinishedDetectorProjectJob.class);

    private final Duration period;
    private final Duration finishedPeriod;
    private final Clock clock;
    private final JeffreyDirs jeffreyDirs;
    private final PlatformRepositories platformRepositories;
    private final SessionFinishEventEmitter eventEmitter;

    public SessionFinishedDetectorProjectJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period,
            Duration finishedPeriod,
            Clock clock,
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            SessionFinishEventEmitter eventEmitter) {

        super(workspacesManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
        this.period = period;
        this.finishedPeriod = finishedPeriod;
        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.eventEmitter = eventEmitter;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RepositoryStorage repositoryStorage,
            SessionFinishedDetectorProjectJobDescriptor jobDescriptor,
            JobContext context) {

        ProjectInfo projectInfo = manager.info();
        ProjectRepositoryRepository projectRepositoryRepository =
                platformRepositories.newProjectRepositoryRepository(projectInfo.id());

        List<RepositorySessionInfo> unfinishedSessions = projectRepositoryRepository.findUnfinishedSessions();

        if (unfinishedSessions.isEmpty()) {
            return;
        }

        List<RepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            LOG.warn("No repository info found for project: projectId={}", projectInfo.id());
            return;
        }
        RepositoryInfo repositoryInfo = repositoryInfos.getFirst();

        for (RepositorySessionInfo sessionInfo : unfinishedSessions) {
            processSession(projectInfo, projectRepositoryRepository, repositoryInfo, sessionInfo);
        }
    }

    private void processSession(
            ProjectInfo projectInfo,
            ProjectRepositoryRepository projectRepositoryRepository,
            RepositoryInfo repositoryInfo,
            RepositorySessionInfo sessionInfo) {

        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);
        StatusStrategy strategy = createStatusStrategy(sessionInfo);
        RecordingStatus status = strategy.determineStatus(sessionPath);

        if (status == RecordingStatus.FINISHED) {
            // Mark session as finished in the database
            projectRepositoryRepository.markSessionFinished(sessionInfo.sessionId(), clock.instant());

            // Emit SESSION_FINISHED event
            eventEmitter.emitSessionFinished(projectInfo, sessionInfo);
        }
    }

    private Path resolveSessionPath(RepositoryInfo repositoryInfo, RepositorySessionInfo sessionInfo) {
        String workspacesPath = repositoryInfo.workspacesPath();
        Path resolvedWorkspacesPath = workspacesPath == null ? jeffreyDirs.workspaces() : Path.of(workspacesPath);
        return resolvedWorkspacesPath
                .resolve(repositoryInfo.relativeWorkspacePath())
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(sessionInfo.relativeSessionPath());
    }

    private StatusStrategy createStatusStrategy(RepositorySessionInfo sessionInfo) {
        if (sessionInfo.finishedFile() != null) {
            return new WithDetectionFileStrategy(sessionInfo.finishedFile(), finishedPeriod, clock);
        } else {
            return new WithoutDetectionFileStrategy(finishedPeriod, clock);
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
