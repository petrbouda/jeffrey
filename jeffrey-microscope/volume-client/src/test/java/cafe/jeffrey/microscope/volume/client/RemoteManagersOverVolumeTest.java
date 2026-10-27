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
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.manager.InstanceWithSessions;
import cafe.jeffrey.microscope.remote.client.manager.RemoteInstancesManager;
import cafe.jeffrey.microscope.remote.client.manager.RemoteRepositoryManager;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote managers Microscope builds its pages from, read off a recording volume: an instance
 * with when its data ends, its sessions with the sizes they arrived with, and a session's failed
 * flag.
 */
class RemoteManagersOverVolumeTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);
    private static final ProjectScope ORDERS = new ProjectScope("prod", "p-orders");
    private static final String BUSY_INSTANCE = "pod-busy";
    private static final String QUIET_INSTANCE = "pod-quiet";
    private static final String CHUNK_OF_1000_BYTES = "x".repeat(1000);

    @TempDir
    Path root;

    private RemoteClients clients;

    /**
     * Two instances: one with an older session holding one chunk, a session that ended empty, and
     * the newest one with two chunks a minute apart; and one whose only session has written nothing.
     */
    @BeforeEach
    void setUp() throws IOException {
        Path project = VolumeWriter.project(Files.createDirectories(root.resolve("prod")), "p-orders", "orders", T0);

        Path busy = VolumeWriter.instance(project, BUSY_INSTANCE, T0);
        Path older = VolumeWriter.session(busy, "session-older", T0.plusSeconds(60), null);
        VolumeWriter.file(older, "profile-20260220-120100.jfr", CHUNK_OF_1000_BYTES);
        VolumeWriter.session(busy, "session-failed", T0.plusSeconds(900), null);
        Path newest = VolumeWriter.session(busy, "session-newest", T0.plusSeconds(1200), null);
        VolumeWriter.file(newest, "profile-20260220-122000.jfr", CHUNK_OF_1000_BYTES);
        VolumeWriter.file(newest, "profile-20260220-122100.jfr", CHUNK_OF_1000_BYTES);

        Path quiet = VolumeWriter.instance(project, QUIET_INSTANCE, T0);
        VolumeWriter.session(quiet, "session-quiet", T0, null);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        clients = VolumeRemoteClients.of(new VolumeSource(new FilesystemVolume(root), clock, "test-volume", false));
    }

    private RemoteInstancesManager instances() {
        return new RemoteInstancesManager(ORDERS, clients.instances());
    }

    private static ProjectInstanceInfo byId(List<ProjectInstanceInfo> instances, String id) {
        return instances.stream().filter(instance -> instance.id().equals(id)).findFirst().orElseThrow();
    }

    private static RecordingSession sessionById(List<RecordingSession> sessions, String id) {
        return sessions.stream().filter(session -> session.id().equals(id)).findFirst().orElseThrow();
    }

    @Nested
    class FindAll {

        /** The newest session's last chunk started at 12:21 and chunks are a minute long. */
        @Test
        void anInstanceDataEndsWithItsNewestSessionsLastChunk() {
            List<ProjectInstanceInfo> instances = instances().findAll();

            assertEquals(2, instances.size());
            ProjectInstanceInfo busy = byId(instances, BUSY_INSTANCE);
            assertEquals(Instant.parse("2026-02-20T12:22:00Z"), busy.lastDataAt());
            assertEquals(3, busy.sessionCount());
        }

        @Test
        void anInstanceWhoseNewestSessionHoldsNoRecordingHasNoDataYet() {
            ProjectInstanceInfo quiet = byId(instances().findAll(), QUIET_INSTANCE);

            assertNull(quiet.lastDataAt());
            assertEquals(1, quiet.sessionCount());
        }
    }

    @Nested
    class Find {

        @Test
        void readsTheInstanceWithItsSessionsNewestFirst() {
            InstanceWithSessions found = instances().find(BUSY_INSTANCE, Optional.empty()).orElseThrow();

            assertEquals(BUSY_INSTANCE, found.instance().id());
            assertEquals(List.of("session-newest", "session-failed", "session-older"),
                    found.sessions().stream().map(RecordingSession::id).toList());
            assertTrue(found.sessions().getFirst().newest());
            assertFalse(found.sessions().get(1).newest());
        }

        @Test
        void sessionsCarryTheirFilesSoOpeningOneAsksForNothing() {
            RecordingSession older = sessionById(
                    instances().find(BUSY_INSTANCE, Optional.empty()).orElseThrow().sessions(), "session-older");

            assertEquals(1000L, older.totalSizeBytes());
            assertEquals(1, older.fileCount());
            assertEquals(List.of("profile-20260220-120100.jfr"),
                    older.files().stream().map(file -> file.name()).toList());
        }

        @Test
        void anEmptySessionANewerOneEndedFailed() {
            List<RecordingSession> sessions =
                    instances().find(BUSY_INSTANCE, Optional.empty()).orElseThrow().sessions();

            assertTrue(sessionById(sessions, "session-failed").failed());
            assertFalse(sessionById(sessions, "session-older").failed());
        }

        @Test
        void theNewestSessionIsNeverFailedEvenWhileEmpty() {
            RecordingSession quiet = instances().find(QUIET_INSTANCE, Optional.empty()).orElseThrow()
                    .sessions().getFirst();

            assertFalse(quiet.failed());
            assertTrue(quiet.newest());
        }

        @Test
        void anUnknownInstanceIsEmpty() {
            assertTrue(instances().find("ghost", Optional.empty()).isEmpty());
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
            RecordingSession older = sessionById(
                    repository().listRecordingSessions(RecordingSessionFilter.ALL), "session-older");

            assertEquals(1, older.fileCount());
            assertEquals(1000L, older.totalSizeBytes());
            assertTrue(older.files().isEmpty());
        }
    }
}
