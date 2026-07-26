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
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.SessionFileDetectorProjectJobDescriptor;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventConverter;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Announces finished recording and artifact files as workspace events, so clients can react to
 * each rotated JFR chunk or fresh heap dump instead of waiting for the whole session.
 *
 * <p><b>Stateless by design.</b> There is no watermark table: the job re-offers every candidate
 * on every tick and relies on the queue's unique {@code dedup_key} index to swallow repeats.
 * That works because {@code RepositoryFile.id()} is a stable, workspace-relative path with the
 * recording extension stripped, so a chunk keeps one identity even after LZ4 compression.
 *
 * <p><b>Why {@code maxFileAge} is a correctness bound, not a performance knob.</b> Deduplication
 * only holds while the dedup row exists, and {@code WorkspaceEventsCleanerJob} deletes queue rows
 * past {@code queue-events-retention}. A file that outlives its dedup row would be announced a
 * second time — and retained (crash-pinned) sessions are never reclaimed by any cleaner, so their
 * files live forever. Bounding candidacy by file age keeps every candidate inside the retention
 * window, which closes that hole. It also stops a first deployment against an existing repository
 * from dumping the entire file history into the event log.
 */
public class SessionFileDetectorProjectJob extends RepositoryProjectJob<SessionFileDetectorProjectJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFileDetectorProjectJob.class);

    private static final WorkspaceEventCreator CREATOR = WorkspaceEventCreator.SESSION_FILE_DETECTOR_JOB;

    private final Duration period;
    private final Clock clock;
    private final WorkspaceEventPublisher workspaceEventPublisher;

    public SessionFileDetectorProjectJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            SessionFileDetectorProjectJobDescriptor jobDescriptor,
            Duration period,
            Clock clock,
            WorkspaceEventPublisher workspaceEventPublisher) {

        super(workspacesManager, repositoryStorageFactory, jobDescriptor);
        this.period = period;
        this.clock = clock;
        this.workspaceEventPublisher = workspaceEventPublisher;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RepositoryStorage repositoryStorage,
            SessionFileDetectorProjectJobDescriptor jobDescriptor,
            JobContext context) {

        ProjectInfo projectInfo = manager.info();
        Instant now = clock.instant();
        Instant oldestAllowed = now.minus(jobDescriptor.maxFileAge());

        // Listing without files is a database-only read; only the sessions that survive the age
        // filter pay for a directory walk.
        List<RecordingSession> candidateSessions = repositoryStorage.listSessions(false).stream()
                .filter(session -> isWithinWindow(session, oldestAllowed))
                .toList();

        if (candidateSessions.isEmpty()) {
            return;
        }

        // Keyed by file id so the compression window — where foo.jfr and foo.jfr.lz4 both exist
        // and resolve to the same id — cannot produce two events for one file.
        Map<String, WorkspaceEvent> events = new LinkedHashMap<>();

        for (RecordingSession session : candidateSessions) {
            if (events.size() >= jobDescriptor.maxEventsPerTick()) {
                break;
            }
            collectSessionEvents(repositoryStorage, projectInfo, session, jobDescriptor, oldestAllowed, now, events);
        }

        if (events.isEmpty()) {
            return;
        }

        List<WorkspaceEvent> batch = List.copyOf(events.values());
        workspaceEventPublisher.publishBatch(projectInfo.workspaceId(), batch);

        // Candidates, not new events: appendBatch returns void, so the job cannot know how many
        // rows the dedup index actually accepted. Kept at DEBUG for that reason.
        LOG.debug("Offered session file events: project_id={} sessions={} candidates={}",
                projectInfo.id(), candidateSessions.size(), batch.size());
    }

    private void collectSessionEvents(
            RepositoryStorage repositoryStorage,
            ProjectInfo projectInfo,
            RecordingSession session,
            SessionFileDetectorProjectJobDescriptor jobDescriptor,
            Instant oldestAllowed,
            Instant now,
            Map<String, WorkspaceEvent> events) {

        // singleSession is what resolves the session's own status and hands it down to the file
        // listing, which is what marks the chunk still being written as ACTIVE. The job must never
        // walk the directory itself, or it would lose that guard.
        Optional<RecordingSession> withFiles = repositoryStorage.singleSession(session.id(), true);
        if (withFiles.isEmpty()) {
            return;
        }

        for (RepositoryFile file : withFiles.get().files()) {
            if (events.size() >= jobDescriptor.maxEventsPerTick()) {
                return;
            }
            if (!isAnnounceable(file, jobDescriptor, oldestAllowed, now)) {
                continue;
            }
            events.putIfAbsent(file.id(), WorkspaceEventConverter.sessionFileCreated(
                    now, projectInfo, withFiles.get(), file, CREATOR));
        }
    }

    /**
     * An unfinished session is always in scope, however long ago it started: a JVM that has been
     * streaming for weeks still rotates fresh chunks, and filtering on its creation time would
     * silently stop announcing files for exactly the longest-lived services. A finished session
     * stays in scope only while its finish time is inside the window, after which it can never
     * gain files again.
     *
     * <p>The retention invariant does not depend on this filter — every file is age-checked
     * individually — so admitting an old long-running session is safe: its stale files are
     * rejected per file, its fresh ones are announced.
     */
    private static boolean isWithinWindow(RecordingSession session, Instant oldestAllowed) {
        if (session.finishedAt() == null) {
            return true;
        }
        return !session.finishedAt().isBefore(oldestAllowed);
    }

    private static boolean isAnnounceable(
            RepositoryFile file,
            SessionFileDetectorProjectJobDescriptor jobDescriptor,
            Instant oldestAllowed,
            Instant now) {

        // Order matters: the status check must come before any type filtering, because the
        // repository storage expresses "still being written" by downgrading the newest RECORDING
        // file of an unfinished session to ACTIVE.
        if (file.status() != RecordingStatus.FINISHED) {
            return false;
        }

        boolean recording = file.isRecordingFile();
        if (!recording && !file.isArtifactFile()) {
            // Skips TEMPORARY async-profiler rotation files and UNRECOGNIZED entries — the latter
            // may be extensionless and could otherwise collide ids with a real recording.
            return false;
        }

        if (file.createdAt() == null || file.createdAt().isBefore(oldestAllowed)) {
            return false;
        }

        if (recording) {
            // Empty chunks are skipped by compression too ("profiler stopped before any events"),
            // so announcing one promises a download that will never be useful.
            return file.size() != null && file.size() > 0;
        }

        // Artifacts have no ACTIVE marker, so a heap dump mid-write looks finished. Require it to
        // have been untouched for a moment before treating it as complete.
        return hasSettled(file, jobDescriptor.artifactSettleThreshold(), now);
    }

    /**
     * Whether the file has been untouched long enough to count as complete.
     *
     * <p>Deliberately reads the last-modified time rather than {@code file.createdAt()}: for an
     * artifact that value is the filesystem <i>creation</i> time, which is set once when the file
     * appears and never moves. A heap dump written over several minutes would sail past a
     * creation-time check while still growing; only mtime advances as bytes arrive.
     */
    private static boolean hasSettled(RepositoryFile file, Duration settleThreshold, Instant now) {
        if (settleThreshold.isZero()) {
            return true;
        }
        if (file.filePath() == null) {
            return true;
        }
        try {
            Instant modifiedAt = FileSystemUtils.modifiedAt(file.filePath());
            return !modifiedAt.isAfter(now.minus(settleThreshold));
        } catch (RuntimeException e) {
            // A file that vanished mid-scan is not announceable; treating it as unsettled means
            // the next tick decides, rather than emitting an event for something already gone.
            LOG.debug("Cannot read modification time, deferring file: file={}", file.filePath(), e);
            return false;
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.SESSION_FILE_DETECTOR;
    }
}
