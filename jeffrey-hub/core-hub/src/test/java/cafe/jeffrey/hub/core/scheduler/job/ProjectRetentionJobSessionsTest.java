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

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The session rules of a retention pass: the age window measured from a session's last data, the
 * per-instance cap and the trim of each instance's newest session. The disk budget is far above
 * anything here, so the quota never acts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectRetentionJobSessionsTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final Duration PAST_RETENTION = RETENTION.plus(Duration.ofDays(1));
    private static final long MB = 1024L * 1024L;
    private static final String INSTANCE_ID = "inst-1";
    private static final int MAX_SESSIONS = 10;

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    RepositoryStorage storage;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryManager repositoryManager;

    @Mock
    StorageOverviewCache storageOverview;

    private ProjectRetentionJob job;

    @BeforeEach
    void setUp() {
        ProjectInfo projectInfo = new ProjectInfo("proj-1", "my-project", "ws-1", Optional.of(NOW));

        when(projectManager.info()).thenReturn(projectInfo);
        when(projectManager.repositoryManager()).thenReturn(repositoryManager);
        when(repositoryManager.deleteRecordingSession(anyString())).thenReturn(SessionDeletion.SESSION_DELETED);
        when(projectManager.repositoryStorage()).thenReturn(storage);
        when(storage.instances()).thenReturn(List.of(instance(INSTANCE_ID)));

        job = job(MAX_SESSIONS);
    }

    private ProjectRetentionJob job(int maxSessions) {
        return job(RETENTION, RETENTION, maxSessions);
    }

    private ProjectRetentionJob job(
            Duration sessionRetention, Duration activeSessionFileRetention, int maxSessions) {

        JobConfig config = new JobConfig(true, Duration.ofMinutes(5), Map.of(
                "session-retention", sessionRetention.toDays() + "d",
                "active-session-file-retention", activeSessionFileRetention.toDays() + "d",
                "max-sessions", Integer.toString(maxSessions),
                "max-size", "1T",
                "instance-retention", "14d"));
        return new ProjectRetentionJob(workspacesManager, config, storageOverview, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void execute() {
        job.retain(projectManager);
    }

    private void execute(int maxSessions) {
        job(maxSessions).retain(projectManager);
    }

    private static ProjectInstanceInfo instance(String instanceId) {
        return new ProjectInstanceInfo(instanceId, Optional.of(NOW.minus(Duration.ofDays(30))), 3);
    }

    private static RepositoryFile recording(String id, Instant createdAt, long size) {
        return new RepositoryFile(id, id, createdAt, size, true, null);
    }

    /** A session a newer one has ended, with one recording from its start. */
    private static RecordingSession realSession(String id, Instant createdAt) {
        return session(id, INSTANCE_ID, createdAt, false, recording(id + "-file", createdAt, 10 * MB));
    }

    /** A session a newer one ended before it wrote anything — a prematurely killed process. */
    private static RecordingSession emptySession(String id, Instant createdAt) {
        return session(id, INSTANCE_ID, createdAt, false);
    }

    /** The instance's newest session, holding these files. */
    private static RecordingSession newestSession(String id, Instant createdAt, RepositoryFile... files) {
        return session(id, INSTANCE_ID, createdAt, true, files);
    }

    private static RecordingSession session(
            String id, String instanceId, Instant createdAt, boolean newest, RepositoryFile... files) {

        return new RecordingSession(id, id, instanceId, createdAt, newest, List.of(files));
    }

    private List<String> deletedSessionIds() {
        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(repositoryManager, atLeast(0)).deleteRecordingSession(deleted.capture());
        return deleted.getAllValues();
    }

    @Nested
    class NewestSessionTrim {

        /**
         * The same window at the finer granularity: an instance's newest session keeps every
         * chunk younger than it and its newest recording, whatever its age.
         */
        @Test
        void trimsClosedChunksOlderThanTheWindowOutOfTheNewestSession() {
            RepositoryFile old = recording("old", NOW.minus(PAST_RETENTION), 10 * MB);
            RepositoryFile recent = recording("recent", NOW.minusSeconds(600), 10 * MB);
            RepositoryFile open = recording("open", NOW.minusSeconds(60), 10 * MB);
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("live", NOW.minus(PAST_RETENTION.plusDays(1)), old, recent, open)));

            execute();

            verify(storage).deleteRepositoryFiles("live", List.of("old"));
            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }

        @Test
        void leavesTheNewestRecordingAloneEvenWhenItIsOlderThanTheFileWindow() {
            RepositoryFile onlyChunk = recording("only", NOW.minus(Duration.ofDays(3)), 10 * MB);
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("live", NOW.minus(Duration.ofDays(4)), onlyChunk)));

            job(Duration.ofDays(30), Duration.ofDays(1), MAX_SESSIONS).retain(projectManager);

            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }

        /** An ended session cannot grow: it goes whole by the session window, never trimmed empty. */
        @Test
        void leavesASessionANewerOneHasEndedToTheSessionWindow() {
            RepositoryFile old = recording("old", NOW.minus(Duration.ofDays(3)), 10 * MB);
            RepositoryFile last = recording("last", NOW.minus(Duration.ofDays(2)), 10 * MB);
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("live", NOW.minusSeconds(600)),
                    session("ended", INSTANCE_ID, NOW.minus(Duration.ofDays(3)), false, old, last)));

            job(Duration.ofDays(30), Duration.ofDays(1), MAX_SESSIONS).retain(projectManager);

            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
        }
    }

    @Nested
    class AgeOfTheLastData {

        @Test
        void aSessionStillRecordingIsKeptHoweverLongAgoItStarted() {
            RepositoryFile open = recording("open", NOW.minusSeconds(60), 10 * MB);
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("live", NOW.minus(PAST_RETENTION.plusDays(30)), open)));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }

        /** An instance that stopped has no newer session to end its last one; the window does. */
        @Test
        void theLastSessionOfAStoppedInstanceGoesOnceItsLastDataIsPastTheWindow() {
            RepositoryFile last = recording("last", NOW.minus(PAST_RETENTION), 10 * MB);
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("stopped", NOW.minus(PAST_RETENTION.plusDays(1)), last),
                    realSession("older", NOW.minus(PAST_RETENTION.plusDays(2)))));

            execute();

            assertEquals(List.of("stopped", "older"), deletedSessionIds());
        }

        @Test
        void aNewestSessionThatNeverRecordedGoesOnceItsStartIsPastTheWindow() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("silent", NOW.minus(PAST_RETENTION))));

            execute();

            assertEquals(List.of("silent"), deletedSessionIds());
        }

        @Test
        void sessionsInsideTheWindowStayWhateverTheyHold() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("live", NOW.minusSeconds(600)),
                    emptySession("young-empty", NOW.minus(Duration.ofDays(1))),
                    realSession("real", NOW.minus(Duration.ofDays(2)))));

            execute();

            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }

        @Test
        void loadsSessionsWithFilesSoTheirLastDataIsVisible() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of());

            execute();

            verify(storage).listSessions(SessionDetail.WITH_FILES);
            verify(storage, never()).listSessions(SessionDetail.HEADERS);
        }
    }

    @Nested
    class IndependentWindows {

        private static final Duration SHORT = Duration.ofDays(1);
        private static final Duration LONG = Duration.ofDays(30);
        private static final Duration AGE = Duration.ofDays(3);

        @Test
        void shortFileWindowTrimsTheNewestSessionWhileEndedSessionsOfTheSameAgeStay() {
            RepositoryFile old = recording("old", NOW.minus(AGE), 10 * MB);
            RepositoryFile open = recording("open", NOW.minusSeconds(60), 10 * MB);
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("live", NOW.minus(AGE), old, open),
                    realSession("newer-ended", NOW.minus(AGE.plusDays(1))),
                    realSession("older-ended", NOW.minus(AGE.plusDays(2)))));

            job(LONG, SHORT, MAX_SESSIONS).retain(projectManager);

            verify(storage).deleteRepositoryFiles("live", List.of("old"));
            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }

        @Test
        void shortSessionWindowDeletesEndedSessionsWhileTheNewestKeepsItsChunks() {
            RepositoryFile old = recording("old", NOW.minus(AGE), 10 * MB);
            RepositoryFile open = recording("open", NOW.minusSeconds(60), 10 * MB);
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of(
                    newestSession("live", NOW.minus(AGE), old, open),
                    realSession("newer-ended", NOW.minus(AGE.plusDays(1))),
                    realSession("older-ended", NOW.minus(AGE.plusDays(2)))));

            job(SHORT, LONG, MAX_SESSIONS).retain(projectManager);

            assertEquals(List.of("newer-ended", "older-ended"), deletedSessionIds());
            verify(storage, never()).deleteRepositoryFiles(anyString(), any());
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

        /** The instance's newest session, recording since half an hour ago. */
        private void live(String id) {
            sessions.add(newestSession(id, NOW.minus(Duration.ofMinutes(30))));
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

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(sessions);

            execute(10);

            verify(repositoryManager, never()).deleteRecordingSession(anyString());
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

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(sessions);

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

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(sessions);

            execute(3);

            assertEquals(List.of("real-4"), deletedSessionIds());
        }

        @Test
        void theNewestSessionTakesTheFirstSlotAndIsNeverOverTheCap() {
            live("live");
            real("real-1");
            real("real-2");
            real("real-3");

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(sessions);

            execute(3);

            // The newest session takes unit 0, pushing the oldest real session over the cap
            assertEquals(List.of("real-3"), deletedSessionIds());
        }

        @Test
        void everySessionBeyondTheCapGoesWhateverItHolds() {
            live("live");
            empty("empty-1");
            real("real-1");
            empty("empty-2");

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(sessions);

            execute(2);

            // Units: [live], [empty-1], [real-1], [empty-2]
            assertEquals(List.of("real-1", "empty-2"), deletedSessionIds());
        }

        @Test
        void capAppliesPerInstanceIndependently() {
            when(storage.instances()).thenReturn(List.of(instance(INSTANCE_ID), instance("inst-2")));

            real("real-1");
            real("real-2");
            real("real-3");
            for (int i = 1; i <= 3; i++) {
                Instant createdAt = NOW.minus(Duration.ofHours(++ageHours));
                sessions.add(session("other-real-" + i, "inst-2", createdAt, i == 1,
                        recording("other-file-" + i, createdAt, 10 * MB)));
            }

            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(sessions);

            execute(4);

            // 6 sessions in total, but each instance holds only 3 logical sessions — under the cap
            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }
    }

    @Nested
    class DegenerateInput {

        @Test
        void toleratesEmptyRepository() {
            when(storage.listSessions(SessionDetail.WITH_FILES)).thenReturn(List.of());

            assertDoesNotThrow(() -> execute());
            verify(repositoryManager, never()).deleteRecordingSession(anyString());
        }
    }
}
