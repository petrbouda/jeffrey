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

import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.InstanceLifecycleEventEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProjectInstanceSessionCleanerJobDescriptor;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectInstanceSessionCleanerJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final Duration PAST_RETENTION = RETENTION.plus(Duration.ofDays(1));
    private static final long MB = 1024L * 1024L;
    private static final String INSTANCE_ID = "inst-1";
    private static final int MAX_SESSIONS = 10;

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    RepositoryStorage.Factory storageFactory;

    @Mock
    RepositoryStorage storage;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryManager repositoryManager;

    @Mock
    HubPlatformRepositories platformRepositories;

    @Mock
    ProjectInstanceRepository instanceRepository;

    @Mock
    InstanceLifecycleEventEmitter instanceLifecycleEventEmitter;

    private ProjectInstanceSessionCleanerJob job;

    @BeforeEach
    void setUp() {
        ProjectInfo projectInfo = new ProjectInfo(
                "proj-1", null, "my-project", null, null, "ws-1", NOW, NOW, Map.of(), null);

        when(projectManager.info()).thenReturn(projectInfo);
        when(projectManager.repositoryManager()).thenReturn(repositoryManager);
        when(storageFactory.apply(any())).thenReturn(storage);
        when(platformRepositories.newProjectInstanceRepository("proj-1")).thenReturn(instanceRepository);
        when(instanceRepository.find(INSTANCE_ID)).thenReturn(Optional.of(instance(ProjectInstanceStatus.ACTIVE)));

        ProjectInstanceSessionCleanerJobDescriptor descriptor =
                new ProjectInstanceSessionCleanerJobDescriptor(RETENTION.toDays(), ChronoUnit.DAYS, MAX_SESSIONS);

        job = new ProjectInstanceSessionCleanerJob(
                workspacesManager,
                storageFactory,
                descriptor,
                Duration.ofHours(1),
                Clock.fixed(NOW, ZoneOffset.UTC),
                platformRepositories,
                instanceLifecycleEventEmitter);
    }

    private void execute() {
        execute(MAX_SESSIONS);
    }

    private void execute(int maxSessions) {
        job.executeOnRepository(
                projectManager,
                storage,
                new ProjectInstanceSessionCleanerJobDescriptor(RETENTION.toDays(), ChronoUnit.DAYS, maxSessions),
                JobContext.EMPTY);
    }

    private static ProjectInstanceInfo instance(ProjectInstanceStatus status) {
        return instance(INSTANCE_ID, status);
    }

    private static ProjectInstanceInfo instance(String instanceId, ProjectInstanceStatus status) {
        return new ProjectInstanceInfo(
                instanceId, "proj-1", "my-instance", status, NOW.minus(Duration.ofDays(30)),
                null, null, null, 3, null);
    }

    private static RepositoryFile recording(String id, Instant createdAt, long size) {
        return new RepositoryFile(
                id, id, createdAt, size, SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null);
    }

    /** A finished session that produced real data. */
    private static RecordingSession realSession(String id, Instant createdAt) {
        return session(id, createdAt, createdAt.plusSeconds(60), false,
                recording(id + "-file", createdAt, 10 * MB));
    }

    /** A finished session with zero bytes — a prematurely killed process. */
    private static RecordingSession emptySession(String id, Instant createdAt) {
        return session(id, createdAt, createdAt.plusSeconds(5), false);
    }

    /** A still-running session — zero bytes so far, no finish timestamp. */
    private static RecordingSession activeSession(String id, Instant createdAt) {
        return session(id, createdAt, null, false);
    }

    private static RecordingSession session(
            String id, Instant createdAt, Instant finishedAt, boolean retained, RepositoryFile... files) {

        return session(id, INSTANCE_ID, createdAt, finishedAt, retained, files);
    }

    private static RecordingSession session(
            String id, String instanceId, Instant createdAt, Instant finishedAt, boolean retained,
            RepositoryFile... files) {

        RecordingStatus status = finishedAt != null ? RecordingStatus.FINISHED : RecordingStatus.ACTIVE;
        return new RecordingSession(
                id, id, instanceId, createdAt, finishedAt, status, null, null, List.of(files), retained);
    }

    private List<String> deletedSessionIds() {
        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(repositoryManager, org.mockito.Mockito.atLeast(0))
                .deleteRecordingSession(deleted.capture(), eq(WorkspaceEventCreator.PROJECT_INSTANCE_SESSION_CLEANER_JOB));
        return deleted.getAllValues();
    }

    @Nested
    class KeepNewestProtection {

        @Test
        void keepsNewestRealSessionOfLiveInstanceAndDeletesOlderOnes() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    realSession("newest", NOW.minus(PAST_RETENTION)),
                    realSession("older", NOW.minus(PAST_RETENTION.plusDays(1)))));

            execute();

            assertEquals(List.of("older"), deletedSessionIds());
        }

        @Test
        void loadsSessionsWithFilesSoSizesAreVisible() {
            when(storage.listSessions(true)).thenReturn(List.of());

            execute();

            verify(storage).listSessions(true);
            verify(storage, never()).listSessions(false);
        }
    }

    @Nested
    class EmptySessionsSkipProtection {

        @Test
        void emptySessionsNeverConsumeTheKeepNewestSlot() {
            // A crash-looped instance: the newest sessions are all failed empties.
            // The newest REAL session must survive even though it is not at index 0.
            when(storage.listSessions(true)).thenReturn(List.of(
                    emptySession("empty-1", NOW.minus(PAST_RETENTION)),
                    emptySession("empty-2", NOW.minus(PAST_RETENTION.plusDays(1))),
                    emptySession("empty-3", NOW.minus(PAST_RETENTION.plusDays(2))),
                    realSession("interesting", NOW.minus(PAST_RETENTION.plusDays(3)))));

            execute();

            assertEquals(List.of("empty-1", "empty-2", "empty-3"), deletedSessionIds());
        }

        @Test
        void olderRealSessionIsStillDeletedWhenANewerRealSessionExists() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    emptySession("empty-1", NOW.minus(PAST_RETENTION)),
                    realSession("newest-real", NOW.minus(PAST_RETENTION.plusDays(1))),
                    realSession("older-real", NOW.minus(PAST_RETENTION.plusDays(2)))));

            execute();

            assertEquals(List.of("empty-1", "older-real"), deletedSessionIds());
        }
    }

    @Nested
    class AllSessionsEmpty {

        @Test
        void deletesEveryPastAgeEmptySessionOfLiveInstance() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    emptySession("empty-1", NOW.minus(PAST_RETENTION)),
                    emptySession("empty-2", NOW.minus(PAST_RETENTION.plusDays(1)))));

            execute();

            assertEquals(List.of("empty-1", "empty-2"), deletedSessionIds());
        }
    }

    @Nested
    class FinishedInstance {

        @Test
        void noProtectionForFinishedInstances() {
            when(instanceRepository.find(INSTANCE_ID))
                    .thenReturn(Optional.of(instance(ProjectInstanceStatus.FINISHED)));
            when(storage.listSessions(true)).thenReturn(List.of(
                    realSession("newest", NOW.minus(PAST_RETENTION)),
                    realSession("older", NOW.minus(PAST_RETENTION.plusDays(1)))));

            execute();

            assertEquals(List.of("newest", "older"), deletedSessionIds());
        }
    }

    @Nested
    class AgeRule {

        @Test
        void youngEmptySessionSurvivesUntilRetentionExpires() {
            when(storage.listSessions(true)).thenReturn(List.of(
                    emptySession("young-empty", NOW.minus(Duration.ofDays(1))),
                    realSession("real", NOW.minus(Duration.ofDays(2)))));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession(anyString(), any());
        }
    }

    @Nested
    class RetainedAndActiveSessions {

        @Test
        void retainedEmptySessionIsNeverDeleted() {
            RecordingSession pinnedEmpty = session(
                    "pinned-empty", NOW.minus(PAST_RETENTION), NOW.minus(PAST_RETENTION).plusSeconds(5), true);

            when(storage.listSessions(true)).thenReturn(List.of(pinnedEmpty));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession(anyString(), any());
        }

        @Test
        void activeSessionIsNeverDeletedEvenWithZeroBytes() {
            RecordingSession activeEmpty = session("active-empty", NOW.minus(PAST_RETENTION), null, false);

            when(storage.listSessions(true)).thenReturn(List.of(activeEmpty));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession(anyString(), any());
        }
    }

    @Nested
    class MaxSessionsCap {

        private final List<RecordingSession> sessions = new ArrayList<>();
        private int ageHours = 0;

        /** Appends a real session one hour older than the previous one — far inside the age window. */
        private void real(String id) {
            sessions.add(realSession(id, NOW.minus(Duration.ofHours(++ageHours))));
        }

        /** Appends a failed-empty session one hour older than the previous one. */
        private void empty(String id) {
            sessions.add(emptySession(id, NOW.minus(Duration.ofHours(++ageHours))));
        }

        @Test
        void consecutiveFailedRunsCollapseIntoOneLogicalSession() {
            // 3 real + 5 crashed + 3 real + 5 crashed + 2 real = 3+1+3+1+2 = 10 logical sessions
            real("real-1");
            real("real-2");
            real("real-3");
            empty("empty-1");
            empty("empty-2");
            empty("empty-3");
            empty("empty-4");
            empty("empty-5");
            real("real-4");
            real("real-5");
            real("real-6");
            empty("empty-6");
            empty("empty-7");
            empty("empty-8");
            empty("empty-9");
            empty("empty-10");
            real("real-7");
            real("real-8");

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(10);

            verify(repositoryManager, never()).deleteRecordingSession(anyString(), any());
        }

        @Test
        void deletesOldestLogicalSessionsBeyondTheCap() {
            // The 10-unit layout above plus a crash-loop run and a real session behind it
            real("real-1");
            real("real-2");
            real("real-3");
            empty("empty-1");
            empty("empty-2");
            empty("empty-3");
            empty("empty-4");
            empty("empty-5");
            real("real-4");
            real("real-5");
            real("real-6");
            empty("empty-6");
            empty("empty-7");
            empty("empty-8");
            empty("empty-9");
            empty("empty-10");
            real("real-7");
            real("real-8");
            empty("old-empty-1");
            empty("old-empty-2");
            real("old-real");

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(10);

            // Both physical sessions of the over-cap crash-loop unit go, plus the over-cap real one
            assertEquals(List.of("old-empty-1", "old-empty-2", "old-real"), deletedSessionIds());
        }

        @Test
        void capDeletesSessionsYoungerThanTheAgeWindow() {
            real("real-1");
            real("real-2");
            real("real-3");
            real("real-4");

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(3);

            assertEquals(List.of("real-4"), deletedSessionIds());
        }

        @Test
        void activeSessionOccupiesASlotButIsNeverDeleted() {
            sessions.add(activeSession("live", NOW.minus(Duration.ofMinutes(30))));
            real("real-1");
            real("real-2");
            real("real-3");

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(3);

            // The live session takes unit 0, pushing the oldest real session over the cap
            assertEquals(List.of("real-3"), deletedSessionIds());
        }

        @Test
        void overCapActiveSessionIsNeverDeleted() {
            real("real-1");
            sessions.add(activeSession("stale-live", NOW.minus(Duration.ofHours(5))));

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(1);

            verify(repositoryManager, never()).deleteRecordingSession(anyString(), any());
        }

        @Test
        void retainedSessionsAreInvisibleToTheCap() {
            empty("empty-1");
            Instant pinnedAt = NOW.minus(Duration.ofHours(++ageHours));
            sessions.add(session("pinned", pinnedAt, pinnedAt.plusSeconds(60), true,
                    recording("pinned-file", pinnedAt, 10 * MB)));
            empty("empty-2");
            real("real-1");
            real("real-2");

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(2);

            // The pinned session consumes no slot and no longer splits the crash-loop run:
            // units are [empty-1, empty-2], [real-1], [real-2] — only real-2 is over the cap
            assertEquals(List.of("real-2"), deletedSessionIds());
        }

        @Test
        void keepNewestProtectionBeatsTheCapForLiveInstances() {
            sessions.add(activeSession("live", NOW.minus(Duration.ofMinutes(30))));
            empty("empty-1");
            real("real-1");
            empty("empty-2");

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(2);

            // real-1 is over the cap but is the newest finished session with data — protected
            assertEquals(List.of("empty-2"), deletedSessionIds());
        }

        @Test
        void noProtectionFromTheCapForFinishedInstances() {
            when(instanceRepository.find(INSTANCE_ID))
                    .thenReturn(Optional.of(instance(ProjectInstanceStatus.FINISHED)));

            sessions.add(activeSession("live", NOW.minus(Duration.ofMinutes(30))));
            empty("empty-1");
            real("real-1");
            empty("empty-2");

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(2);

            assertEquals(List.of("real-1", "empty-2"), deletedSessionIds());
        }

        @Test
        void capAppliesPerInstanceIndependently() {
            when(instanceRepository.find("inst-2"))
                    .thenReturn(Optional.of(instance("inst-2", ProjectInstanceStatus.ACTIVE)));

            real("real-1");
            real("real-2");
            real("real-3");
            for (int i = 1; i <= 3; i++) {
                Instant createdAt = NOW.minus(Duration.ofHours(++ageHours));
                sessions.add(session("other-real-" + i, "inst-2", createdAt, createdAt.plusSeconds(60), false,
                        recording("other-file-" + i, createdAt, 10 * MB)));
            }

            when(storage.listSessions(true)).thenReturn(sessions);

            execute(4);

            // 6 sessions in total, but each instance holds only 3 logical sessions — under the cap
            verify(repositoryManager, never()).deleteRecordingSession(anyString(), any());
        }
    }

    @Nested
    class DegenerateInput {

        @Test
        void toleratesEmptyRepository() {
            when(storage.listSessions(true)).thenReturn(List.of());

            assertDoesNotThrow(() -> execute());
            verify(repositoryManager, never()).deleteRecordingSession(anyString(), any());
        }

        @Test
        void treatsFilesWithUnknownSizeAsZeroBytes() {
            RepositoryFile unsized = new RepositoryFile(
                    "f1", "f1", NOW.minus(PAST_RETENTION), null,
                    SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null);

            RecordingSession unsizedSession = session(
                    "unsized", NOW.minus(PAST_RETENTION), NOW.minus(PAST_RETENTION).plusSeconds(5), false, unsized);

            when(storage.listSessions(true)).thenReturn(List.of(
                    unsizedSession,
                    realSession("real", NOW.minus(PAST_RETENTION.plusDays(1)))));

            execute();

            // The unsized session counts as empty — the real session keeps the protection slot
            assertEquals(List.of("unsized"), deletedSessionIds());
        }
    }
}
