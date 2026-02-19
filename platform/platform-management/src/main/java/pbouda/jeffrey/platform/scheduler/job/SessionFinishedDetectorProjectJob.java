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
import pbouda.jeffrey.platform.jfr.JfrMessageEmitter;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.streaming.SessionFinisher;
import pbouda.jeffrey.platform.streaming.SessionPaths;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.SessionFinishedDetectorProjectJobDescriptor;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProjectInstanceRepository;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scheduler job that detects when sessions become FINISHED and emits SESSION_FINISHED events.
 * <p>
 * This job periodically checks all sessions that have not been marked as finished yet.
 * For each unfinished session, it uses {@link SessionFinisher} to determine if the session
 * has actually finished based on heartbeat data. When a session is detected as finished, it
 * also checks if the parent instance should be auto-finished.
 */
public class SessionFinishedDetectorProjectJob extends RepositoryProjectJob<SessionFinishedDetectorProjectJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinishedDetectorProjectJob.class);

    private final Duration period;
    private final Duration heartbeatThreshold;
    private final Clock clock;
    private final JeffreyDirs jeffreyDirs;
    private final PlatformRepositories platformRepositories;
    private final SessionFinisher sessionFinisher;
    private final SessionFinishEventEmitter eventEmitter;

    public SessionFinishedDetectorProjectJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period,
            Duration heartbeatThreshold,
            Clock clock,
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            SessionFinishEventEmitter eventEmitter) {

        super(workspacesManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
        this.period = period;
        this.heartbeatThreshold = heartbeatThreshold;
        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.sessionFinisher = sessionFinisher;
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
        ProjectInstanceRepository projectInstanceRepository =
                platformRepositories.newProjectInstanceRepository(projectInfo.id());

        List<ProjectInstanceSessionInfo> unfinishedSessions = projectRepositoryRepository.findUnfinishedSessions();

        if (unfinishedSessions.isEmpty()) {
            return;
        }

        List<RepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            LOG.warn("No repository info found for project: projectId={}", projectInfo.id());
            return;
        }
        RepositoryInfo repositoryInfo = repositoryInfos.getFirst();

        for (ProjectInstanceSessionInfo sessionInfo : unfinishedSessions) {
            processSession(projectInfo, projectRepositoryRepository, projectInstanceRepository, repositoryInfo, sessionInfo);
        }
    }

    private void processSession(
            ProjectInfo projectInfo,
            ProjectRepositoryRepository projectRepositoryRepository,
            ProjectInstanceRepository projectInstanceRepository,
            RepositoryInfo repositoryInfo,
            ProjectInstanceSessionInfo sessionInfo) {

        Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, sessionInfo);

        boolean finished = sessionFinisher.tryFinishFromHeartbeat(
                projectRepositoryRepository, projectInfo, sessionInfo,
                sessionPath, heartbeatThreshold, clock.instant());

        if (finished) {
            // Check for hs_err log (JVM crash) - emit specific alert
            if (containsHsErrLog(sessionPath)) {
                JfrMessageEmitter.jvmCrashDetected(sessionInfo.sessionId(), sessionInfo.instanceId(), projectInfo.id());
            }

            // Check if the instance has any remaining active sessions
            String instanceId = sessionInfo.instanceId();
            if (instanceId != null) {
                boolean hasActiveSessions = projectInstanceRepository.findSessions(instanceId).stream()
                        .anyMatch(s -> s.finishedAt() == null);

                if (!hasActiveSessions) {
                    projectInstanceRepository.markFinished(instanceId, clock.instant());
                    eventEmitter.emitInstanceFinished(projectInfo, instanceId);
                    JfrMessageEmitter.instanceAutoFinished(instanceId, projectInfo.id());
                    LOG.info("Instance transitioned to FINISHED, no active sessions remaining: projectId={} instanceId={}",
                            projectInfo.id(), instanceId);
                }
            }
        }
    }

    private static boolean containsHsErrLog(Path sessionPath) {
        if (!Files.isDirectory(sessionPath)) {
            return false;
        }
        try (Stream<Path> files = Files.list(sessionPath)) {
            return files.anyMatch(SupportedRecordingFile.HS_JVM_ERROR_LOG::matches);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
