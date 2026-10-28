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
import cafe.jeffrey.microscope.model.repository.RecordingSessionHeader;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote managers Microscope builds its pages from, read off a recording volume: an instance
 * as its directory, a page of its sessions named but not read, the sessions with the sizes they
 * arrived with, and a session's failed flag.
 */
class RemoteManagersOverVolumeTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);
    private static final ProjectScope ORDERS = new ProjectScope("prod", "orders");
    private static final String BUSY_INSTANCE = "pod-busy";
    private static final String QUIET_INSTANCE = "pod-quiet";
    private static final String CHUNK_OF_1000_BYTES = "x".repeat(1000);

    private static final String OLDER = VolumeWriter.sessionIdAt(T0.plusSeconds(60));
    private static final String FAILED = VolumeWriter.sessionIdAt(T0.plusSeconds(900));
    private static final String NEWEST = VolumeWriter.sessionIdAt(T0.plusSeconds(1200));
    private static final String QUIET = VolumeWriter.sessionIdAt(T0);

    @TempDir
    Path root;

    private RemoteClients clients;

    /**
     * Two instances: one with an older session holding one chunk, a session that ended empty, and
     * the newest one with two chunks a minute apart; and one whose only session has written nothing.
     */
    @BeforeEach
    void setUp() throws IOException {
        Path project = VolumeWriter.project(Files.createDirectories(root.resolve("prod")), "orders");

        Path busy = VolumeWriter.instance(project, BUSY_INSTANCE);
        Path older = VolumeWriter.session(busy, T0.plusSeconds(60));
        VolumeWriter.file(older, "profile-20260220-120100.jfr", CHUNK_OF_1000_BYTES);
        VolumeWriter.session(busy, T0.plusSeconds(900));
        Path newest = VolumeWriter.session(busy, T0.plusSeconds(1200));
        VolumeWriter.file(newest, "profile-20260220-122000.jfr", CHUNK_OF_1000_BYTES);
        VolumeWriter.file(newest, "profile-20260220-122100.jfr", CHUNK_OF_1000_BYTES);

        Path quiet = VolumeWriter.instance(project, QUIET_INSTANCE);
        VolumeWriter.session(quiet, T0);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        clients = VolumeRemoteClients.of(new VolumeSource(new FilesystemVolume(root), clock, "test-volume", false));
    }

    private RemoteInstancesManager instances() {
        return new RemoteInstancesManager(ORDERS, clients.instances());
    }

    private RemoteRepositoryManager repository() {
        return new RemoteRepositoryManager(
                null, ORDERS, new RepositoryStatistics(21_300L), clients.repository(), clients.files());
    }

    private static RecordingSession sessionById(List<RecordingSession> sessions, String id) {
        return sessions.stream().filter(session -> session.id().equals(id)).findFirst().orElseThrow();
    }

    @Nested
    class FindAll {

        /** Each instance is its directory, as one listing of the project names it. */
        @Test
        void eachInstanceIsItsDirectory() {
            List<ProjectInstanceInfo> instances = instances().findAll();

            assertEquals(List.of(BUSY_INSTANCE, QUIET_INSTANCE), instances.stream().map(ProjectInstanceInfo::id).toList());
            assertEquals("orders", instances.getFirst().projectId());
        }
    }

    @Nested
    class Find {

        @Test
        void namesTheInstancesSessionsNewestFirst() {
            InstanceWithSessions found = instances().find(BUSY_INSTANCE, Optional.empty()).orElseThrow();

            assertEquals(BUSY_INSTANCE, found.instance().id());
            assertEquals(List.of(NEWEST, FAILED, OLDER),
                    found.sessions().stream().map(RecordingSessionHeader::id).toList());
            assertEquals(List.of(true, false, false),
                    found.sessions().stream().map(RecordingSessionHeader::newest).toList());
            assertEquals(T0.plusSeconds(1200), found.sessions().getFirst().createdAt());
            assertFalse(found.hasOlder());
        }

        @Test
        void anUnknownInstanceIsEmpty() {
            assertTrue(instances().find("ghost", Optional.empty()).isEmpty());
        }
    }

    /** A session opened on its own: its files, its size and whether it failed. */
    @Nested
    class OneSession {

        @Test
        void carriesItsFiles() {
            RecordingSession older = repository().recordingSession(OLDER);

            assertEquals(1000L, older.totalSizeBytes());
            assertEquals(1, older.fileCount());
            assertEquals(List.of("profile-20260220-120100.jfr"),
                    older.files().stream().map(file -> file.name()).toList());
        }

        @Test
        void anEmptySessionANewerOneEndedFailed() {
            assertTrue(repository().recordingSession(FAILED).failed());
            assertFalse(repository().recordingSession(OLDER).failed());
        }

        @Test
        void theNewestSessionIsNeverFailedEvenWhileEmpty() {
            RecordingSession quiet = repository().recordingSession(QUIET);

            assertFalse(quiet.failed());
            assertTrue(quiet.newest());
        }

        /** The newest session's last chunk started at 12:21 and chunks are a minute long. */
        @Test
        void itsDataEndsOneChunkAfterItsLastChunkStarted() {
            assertEquals(Instant.parse("2026-02-20T12:22:00Z"), repository().recordingSession(NEWEST).lastDataAt());
        }
    }

    @Nested
    class Repository {

        @Test
        void reportsTheSizeTheProjectArrivedWith() {
            assertEquals(new RepositoryStatistics(21_300L), repository().repositoryStatistics());
        }

        @Test
        void listedSessionsCarryTheirSizeButNoFiles() {
            RecordingSession older = sessionById(repository().listRecordingSessions(RecordingSessionFilter.ALL), OLDER);

            assertEquals(1, older.fileCount());
            assertEquals(1000L, older.totalSizeBytes());
            assertTrue(older.files().isEmpty());
        }
    }
}
