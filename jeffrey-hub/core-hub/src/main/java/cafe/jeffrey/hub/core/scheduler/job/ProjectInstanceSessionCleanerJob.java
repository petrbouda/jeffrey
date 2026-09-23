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
import java.util.function.Function;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.model.repository.RecordingStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProjectInstanceSessionCleanerJob extends RepositoryProjectJob {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstanceSessionCleanerJob.class);

    private static final String PARAM_RETENTION = "retention";
    private static final String PARAM_MAX_SESSIONS = "max-sessions";

    private final Duration period;
    private final Duration duration;
    private final int maxSessions;
    private final Clock clock;

    /**
     * @param config carries two independent retention rules per instance: an age window
     *               ({@code retention}) and a cap of {@code max-sessions} logical sessions,
     *               where a consecutive run of failed-empty sessions (a crash loop) counts as one
     */
    public ProjectInstanceSessionCleanerJob(WorkspacesManager workspacesManager, JobConfig config, Clock clock) {
        super(workspacesManager);
        this.period = config.period();
        this.duration = config.durationParam(PARAM_RETENTION);
        this.maxSessions = config.intParam(PARAM_MAX_SESSIONS);
        this.clock = clock;
        if (maxSessions <= 0) {
            throw new IllegalArgumentException(PARAM_MAX_SESSIONS + " must be positive: " + maxSessions);
        }
    }

    @Override
    protected void executeOnRepository(ProjectManager manager, RepositoryStorage repositoryStorage) {
        String projectName = manager.info().name();
        LOG.debug("Cleaning the project instance sessions: project_name={}", projectName);

        Instant currentTime = clock.instant();
        ProjectInstanceRepository instanceRepo = manager.projectInstanceRepository();
        // One query for every instance of the project rather than one per instance below
        Map<String, ProjectInstanceInfo> instancesById = instanceRepo.findAll().stream()
                .collect(Collectors.toMap(ProjectInstanceInfo::id, Function.identity()));

        // Group ALL non-retained sessions by instance — the still-active session must occupy
        // a slot of the max-sessions cap, so it stays in the list and is excluded only at
        // deletion time. Retained sessions are pinned evidence (a manual pin, or an auto-pin
        // from a detected JVM crash): they never expire and never consume a cap slot.
        // Files must be loaded (listSessions(SessionDetail.WITH_FILES)) so isFailedEmpty() sees real sizes.
        Map<String, List<RecordingSession>> sessionsByInstance = repositoryStorage.listSessions(SessionDetail.WITH_FILES).stream()
                .filter(session -> !session.retained())
                .collect(Collectors.groupingBy(RecordingSession::instanceId));

        List<RecordingSession> candidatesForDeletion = new ArrayList<>();

        for (var entry : sessionsByInstance.entrySet()) {
            String instanceId = entry.getKey();
            InstanceSessionUnits units = InstanceSessionUnits.of(entry.getValue());

            ProjectInstanceInfo instance = instancesById.get(instanceId);
            boolean isFinished = instance != null && instance.status() == ProjectInstanceStatus.FINISHED;

            // Protection slot: the newest FINISHED session that actually produced data.
            // Failed/empty sessions (finished with zero bytes, e.g. a crash-looped container)
            // never consume the keep-newest protection, and the active session must not claim
            // it either — it is already safe, and letting it win would silently un-protect
            // the newest real finished session.
            String protectedSessionId = units.sessionsNewestFirst().stream()
                    .filter(session -> session.finishedAt() != null)
                    .filter(session -> !session.isFailedEmpty())
                    .map(RecordingSession::id)
                    .findFirst()
                    .orElse(null);

            // Two independent rules in one pass: the age window, and the per-instance cap of
            // logical sessions (a consecutive crash-loop run counts as one unit). Sessions in
            // units beyond the cap are deleted even before their age window expires.
            List<List<RecordingSession>> unitList = units.units();
            for (int unitIndex = 0; unitIndex < unitList.size(); unitIndex++) {
                boolean overCap = unitIndex >= maxSessions;
                for (RecordingSession session : unitList.get(unitIndex)) {
                    if (session.finishedAt() == null) {
                        continue;
                    }

                    // Keep the newest non-empty session for non-FINISHED instances
                    if (!isFinished && session.id().equals(protectedSessionId)) {
                        continue;
                    }

                    boolean ageExpired = currentTime.isAfter(session.createdAt().plus(duration));
                    if (ageExpired || overCap) {
                        candidatesForDeletion.add(session);
                    }
                }
            }
        }

        // deleteRecordingSession removes the row, the directory and the audit event, and owns
        // the instance expiring/EXPIRED transition — no post-processing needed here
        candidatesForDeletion.forEach(session ->
                manager.repositoryManager()
                        .deleteRecordingSession(session.id()));

        if (!candidatesForDeletion.isEmpty()) {
            JfrNotificationEmitter.sessionsCleaned(projectName, candidatesForDeletion.size());
        }

        // The same window inside every session still recording: its closed chunks older than
        // it go, the one its profiler holds open stays whatever its age
        Instant cutoff = currentTime.minus(duration);
        sessionsByInstance.values().stream()
                .flatMap(List::stream)
                .filter(session -> session.status() == RecordingStatus.ACTIVE)
                .forEach(session -> trimOlderThan(repositoryStorage, session, cutoff, projectName));
    }

    /**
     * Deletes the session's closed chunks created before {@code cutoff}. The chunk the profiler
     * is still writing is never among them — {@link RecordingSession#finishedRecordings()} is
     * what leaves it out.
     */
    private static void trimOlderThan(
            RepositoryStorage repositoryStorage, RecordingSession session, Instant cutoff, String projectName) {

        List<String> filesToDelete = session.finishedRecordings().stream()
                .filter(file -> file.createdAt().isBefore(cutoff))
                .map(RepositoryFile::id)
                .toList();

        if (filesToDelete.isEmpty()) {
            return;
        }

        repositoryStorage.deleteRepositoryFiles(session.id(), filesToDelete);
        LOG.info("Deleted expired recordings from live session: project_name={} session_id={} count={}",
                projectName, session.id(), filesToDelete.size());
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECT_INSTANCE_SESSION_CLEANER;
    }
}
