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

package cafe.jeffrey.volumescheduler.retention;

import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import cafe.jeffrey.volumescheduler.scheduler.JobType;
import cafe.jeffrey.volumescheduler.storage.LatestStorageOverview;
import cafe.jeffrey.volumescheduler.storage.ProjectStorage;
import cafe.jeffrey.volumescheduler.storage.StorageOverview;
import cafe.jeffrey.volumescheduler.volume.ProjectInstance;
import cafe.jeffrey.volumescheduler.volume.ProjectRef;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.RecordingSessionFile;
import cafe.jeffrey.volumescheduler.volume.VolumeProject;
import cafe.jeffrey.volumescheduler.volume.VolumeProjects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pass as a whole: one listing per project, rules that see each other's deletions, the
 * storage overview measured from what is left, and stale instances.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Duration INSTANCE_RETENTION = Duration.ofDays(14);
    private static final long MB = 1024L * 1024L;
    private static final ProjectRef REF = new ProjectRef("ws-1", "prj-1");
    private static final RetentionPolicy POLICY = new RetentionPolicy(
            Duration.ofDays(7), 10, Duration.ofDays(7), 100 * MB, INSTANCE_RETENTION);

    @Mock
    VolumeProject project;

    @Mock
    VolumeProjects projects;

    private LatestStorageOverview storageOverview;
    private RetentionJob job;

    @BeforeEach
    void setUp() {
        when(project.ref()).thenReturn(REF);
        when(projects.all()).thenReturn(List.of(project));
        when(project.deleteSession(anyString())).thenReturn(SessionDeletion.SESSION_DELETED);

        storageOverview = new LatestStorageOverview(Clock.fixed(NOW, ZoneOffset.UTC));
        job = new RetentionJob(projects, POLICY, storageOverview, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A session a newer one in its instance has ended, with one recording from its start. */
    private static RecordingSession ended(String id, Duration age, long size) {
        Instant createdAt = NOW.minus(age);
        return new RecordingSession(id, "inst-1", createdAt, false,
                List.of(new RecordingSessionFile(id + "-chunk", id + ".jfr", createdAt, size, true, null)));
    }

    @Nested
    class OneListing {

        @Test
        void listsEachProjectsSessionsOnceWithTheirFiles() {
            when(project.sessions()).thenReturn(List.of(ended("s-1", Duration.ofDays(1), 10 * MB)));

            job.run();

            verify(project, times(1)).sessions();
        }

        /**
         * 170 MB against a 100 MB budget, of which the age window frees 80 MB first. The quota sees
         * the 90 MB that is left and deletes nothing more; a listing of its own taken before the
         * age window ran would have had it delete the next-oldest session too.
         */
        @Test
        void theQuotaCountsWhatTheAgeWindowAlreadyFreed() {
            when(project.sessions()).thenReturn(List.of(
                    ended("newest", Duration.ofDays(1), 30 * MB),
                    ended("newer", Duration.ofDays(2), 60 * MB),
                    ended("aged-out", Duration.ofDays(8), 80 * MB)));

            job.run();

            verify(project).deleteSession("aged-out");
            verify(project, never()).deleteSession("newer");
            verify(project, never()).deleteSession("newest");
        }
    }

    @Nested
    class StorageOverviewFromThePass {

        @Test
        void publishesWhatRemainsOnceTheRulesRan() {
            when(project.sessions()).thenReturn(List.of(
                    ended("newest", Duration.ofDays(1), 30 * MB),
                    ended("aged-out", Duration.ofDays(8), 80 * MB)));

            job.run();

            StorageOverview overview = storageOverview.get();
            assertEquals(Optional.of(NOW), overview.measuredAt());
            ProjectStorage measured = overview.projects().getFirst();
            assertEquals(REF, measured.project());
            assertEquals(30 * MB, measured.totalBytes());
            assertEquals(1, measured.totalFiles());
        }

        @Test
        void aFailingProjectDoesNotStopTheOthers() {
            VolumeProject broken = mock(VolumeProject.class);
            when(broken.ref()).thenReturn(new ProjectRef("ws-1", "broken"));
            when(broken.sessions()).thenThrow(new IllegalStateException("volume"));
            when(projects.all()).thenReturn(List.of(broken, project));
            when(project.sessions()).thenReturn(List.of(ended("s-1", Duration.ofDays(1), 10 * MB)));

            job.run();

            assertEquals(List.of(REF), storageOverview.get().projects().stream()
                    .map(ProjectStorage::project)
                    .toList());
        }
    }

    @Nested
    class StaleInstances {

        private static final Instant LONG_AGO = NOW.minus(INSTANCE_RETENTION).minusSeconds(1);

        private static ProjectInstance instance(String id, Optional<Instant> createdAt, int sessions) {
            return new ProjectInstance(id, createdAt, sessions);
        }

        @BeforeEach
        void noSessions() {
            when(project.sessions()).thenReturn(List.of());
        }

        @Test
        void deletesAnInstanceThatNeverStreamedASession() {
            when(project.instances()).thenReturn(List.of(
                    instance("abandoned", Optional.of(LONG_AGO), 0),
                    instance("young", Optional.of(NOW.minusSeconds(60)), 0),
                    instance("with-sessions", Optional.of(LONG_AGO), 2)));

            job.run();

            verify(project).deleteInstance("abandoned");
            verify(project, never()).deleteInstance("young");
            verify(project, never()).deleteInstance("with-sessions");
        }

        /** A filesystem that cannot say when the directory was made: nothing is deleted on a guess. */
        @Test
        void keepsAnInstanceWhoseAgeIsUnknown() {
            when(project.instances()).thenReturn(List.of(instance("unknown", Optional.empty(), 0)));

            job.run();

            verify(project, never()).deleteInstance("unknown");
        }

        @Test
        void aDirectoryThatCannotBeDeletedDoesNotStopTheNextInstance() {
            when(project.instances()).thenReturn(List.of(
                    instance("first", Optional.of(LONG_AGO), 0),
                    instance("second", Optional.of(LONG_AGO), 0)));
            doThrow(new IllegalStateException("busy")).when(project).deleteInstance("first");

            job.run();

            verify(project).deleteInstance("second");
        }
    }

    @Nested
    class Summary {

        @Test
        void describesWhatThePassDeletedAndWhatIsLeft() {
            when(project.sessions()).thenReturn(List.of(
                    ended("newest", Duration.ofDays(1), 30 * MB),
                    ended("aged-out", Duration.ofDays(8), 80 * MB)));

            String summary = job.run();

            assertTrue(summary.startsWith("Deleted 1 session, 0 files and 0 instances; "), summary);
            assertTrue(summary.endsWith(" across 1 project"), summary);
        }

        @Test
        void isTheRetentionJob() {
            assertEquals(JobType.RETENTION, job.type());
        }
    }
}
