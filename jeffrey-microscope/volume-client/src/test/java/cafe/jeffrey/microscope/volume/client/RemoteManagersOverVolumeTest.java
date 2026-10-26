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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.dto.InstanceDetailResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceSessionDetailResponse;
import cafe.jeffrey.microscope.remote.client.environment.SessionEnvironmentReader;
import cafe.jeffrey.microscope.remote.client.manager.InstanceWithSessions;
import cafe.jeffrey.microscope.remote.client.manager.RemoteInstancesManager;
import cafe.jeffrey.microscope.remote.client.manager.RemoteRepositoryManager;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.liveness.SessionDeadlines;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote managers Microscope builds its pages from, read off a recording volume: an instance's
 * lifecycle, its sessions with the sizes they arrived with, and a session's failed flag.
 */
class RemoteManagersOverVolumeTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);
    private static final SessionDeadlines DEADLINES =
            new SessionDeadlines(Duration.ofSeconds(10), Duration.ofMinutes(1));
    private static final ProjectScope ORDERS = new ProjectScope("prod", "p-orders");
    private static final String LIVE_INSTANCE = "pod-live";
    private static final String FINISHED_INSTANCE = "pod-done";
    private static final String CHUNK_OF_1000_BYTES = "x".repeat(1000);

    @TempDir
    Path root;

    private RemoteClients clients;

    /**
     * Two instances: one still recording, with a finished session holding one chunk, a session
     * that finished empty, and the live one; and one whose only session finished a minute in.
     */
    @BeforeEach
    void setUp() throws IOException {
        Path project = VolumeWriter.project(Files.createDirectories(root.resolve("prod")), "p-orders", "orders", T0);

        Path live = VolumeWriter.instance(project, LIVE_INSTANCE, T0);
        Path finished = VolumeWriter.session(live, "session-finished", T0.plusSeconds(60), null);
        VolumeWriter.finished(finished, T0.plusSeconds(600));
        VolumeWriter.file(finished, "profile-20260220-120100.jfr", CHUNK_OF_1000_BYTES);
        VolumeWriter.finished(VolumeWriter.session(live, "session-failed", T0.plusSeconds(900), null),
                T0.plusSeconds(905));
        VolumeWriter.heartbeat(VolumeWriter.session(live, "session-live", T0.plusSeconds(1200), null), NOW);

        Path done = VolumeWriter.instance(project, FINISHED_INSTANCE, T0);
        VolumeWriter.finished(VolumeWriter.session(done, "session-done", T0, null), T0.plusSeconds(60));

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FilesystemVolume volume = new FilesystemVolume(root);
        clients = VolumeRemoteClients.of(new VolumeSource(
                volume, new SessionLiveness(clock, volume, DEADLINES), clock, "test-volume", false));
    }

    private RemoteInstancesManager instances() {
        // No session here is downloaded for its environment, so the reader is never asked
        SessionEnvironmentReader noEnvironment = new SessionEnvironmentReader(null, null);
        return new RemoteInstancesManager(ORDERS, clients.instances(), clients.repository(), noEnvironment);
    }

    private static ProjectInstanceInfo byId(List<ProjectInstanceInfo> instances, String id) {
        return instances.stream().filter(instance -> instance.id().equals(id)).findFirst().orElseThrow();
    }

    @Nested
    class FindAll {

        @Test
        void carriesTheLifecycleWithoutListingSessions() {
            List<ProjectInstanceInfo> instances = instances().findAll(false);

            assertEquals(2, instances.size());
            ProjectInstanceInfo done = byId(instances, FINISHED_INSTANCE);
            assertEquals(ProjectInstanceStatus.FINISHED, done.status());
            assertEquals(T0.plusSeconds(60), done.finishedAt());
            assertTrue(byId(instances, LIVE_INSTANCE).sessions().isEmpty());
        }

        @Test
        void attachesEachInstanceItsSessions() {
            List<ProjectInstanceInfo> instances = instances().findAll(true);

            ProjectInstanceInfo live = byId(instances, LIVE_INSTANCE);
            assertEquals(3, live.sessions().size());
            assertEquals("session-live", live.activeSessionId());
            assertEquals(1, byId(instances, FINISHED_INSTANCE).sessions().size());
        }
    }

    @Nested
    class Detail {

        @Test
        void sumsTheSizesItsSessionsArrivedWith() {
            InstanceDetailResponse detail = instances().detail(LIVE_INSTANCE).orElseThrow();

            assertEquals(1, detail.stats().fileCount());
            assertEquals(1000L, detail.stats().totalSizeBytes());
            assertEquals(3, detail.instance().sessions().size());
            assertEquals("session-live", detail.instance().activeSessionId());
        }

        @Test
        void unknownInstanceHasNoDetail() {
            assertTrue(instances().detail("ghost").isEmpty());
        }
    }

    @Nested
    class Find {

        @Test
        void readsTheInstanceWithItsSessionsNewestFirst() {
            InstanceWithSessions found = instances().find(LIVE_INSTANCE, Optional.empty()).orElseThrow();

            assertEquals(LIVE_INSTANCE, found.instance().id());
            assertEquals(ProjectInstanceStatus.ACTIVE, found.instance().status());
            assertEquals(List.of("session-live", "session-failed", "session-finished"),
                    found.sessions().stream().map(session -> session.id()).toList());
        }

        @Test
        void sessionsCarryTheirFilesSoOpeningOneAsksForNothing() {
            var finished = instances().find(LIVE_INSTANCE, Optional.empty()).orElseThrow().sessions().get(2);

            assertEquals(1000L, finished.totalSizeBytes());
            assertEquals(1, finished.fileCount());
            assertEquals(List.of("profile-20260220-120100.jfr"),
                    finished.files().stream().map(file -> file.name()).toList());
        }

        @Test
        void anUnknownInstanceIsEmpty() {
            assertTrue(instances().find("ghost", Optional.empty()).isEmpty());
        }
    }

    @Nested
    class SessionDetail {

        @Test
        void carriesTheFailedFlagOfASessionThatFinishedEmpty() {
            InstanceSessionDetailResponse detail =
                    instances().sessionDetail(LIVE_INSTANCE, "session-failed").orElseThrow();

            assertTrue(detail.session().failed());
            assertFalse(detail.session().isActive());
            assertNull(detail.environment());
        }

        @Test
        void aSessionOfAnotherInstanceIsNotFound() {
            Optional<InstanceSessionDetailResponse> detail =
                    instances().sessionDetail(FINISHED_INSTANCE, "session-failed");

            assertTrue(detail.isEmpty());
        }
    }

    @Nested
    class Repository {

        private RemoteRepositoryManager repository() {
            return new RemoteRepositoryManager(
                    null, ORDERS, new RepositoryStatistics(21_300L), clients.repository(), clients.files());
        }

        @Test
        void reportsTheSizeTheProjectArrivedWith() {
            assertEquals(new RepositoryStatistics(21_300L), repository().repositoryStatistics());
        }

        @Test
        void listedSessionsCarryTheirSizeButNoFiles() {
            var finished = repository().listRecordingSessions(RecordingSessionFilter.ALL).stream()
                    .filter(session -> session.id().equals("session-finished"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(1, finished.fileCount());
            assertEquals(1000L, finished.totalSizeBytes());
            assertTrue(finished.files().isEmpty());
        }
    }
}
