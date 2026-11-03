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

package cafe.jeffrey.microscope.connector.local;

import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.ProjectScope;
import cafe.jeffrey.microscope.connector.api.dto.ConnectorProjectResponse;
import cafe.jeffrey.microscope.connector.api.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.connector.volume.VolumeConnectorClients;
import cafe.jeffrey.microscope.connector.volume.VolumeSource;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Microscope reading a volume straight off a directory on this machine, through the same clients the
 * Azure file share answers: opened from its address, read live, refused when declared read-only.
 */
class LocalVolumesTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final ProjectScope ORDERS = new ProjectScope("prod", "orders");
    private static final String DESCRIPTION = ConnectorKind.LOCAL.block();

    @TempDir
    Path root;

    private Path instance;

    @BeforeEach
    void setUp() throws IOException {
        Path workspace = Files.createDirectories(root.resolve("prod"));
        instance = VolumeWriter.instance(VolumeWriter.project(workspace, "orders"), "pod-a");
        VolumeWriter.file(VolumeWriter.session(instance, T0), "rec-20260220-120500+0000.jfr", "chunk");
    }

    private ConnectorClients clients(boolean readOnly) {
        return clients(root, readOnly);
    }

    private static ConnectorClients clients(Path directory, boolean readOnly) {
        LocalDirectoryAddress address = new LocalDirectoryAddress(directory, readOnly);
        return VolumeConnectorClients.of(new VolumeSource(LocalVolumes.open(address), DESCRIPTION, readOnly));
    }

    @Nested
    class Opening {

        @Test
        void reportsItsKindAsItsVersion() {
            assertEquals(DESCRIPTION, clients(false).discovery().info().version());
        }

        /** A directory that is not there is a volume that cannot be reached, not a startup failure. */
        @Test
        void aMissingDirectoryIsUnavailable() {
            ConnectorClients clients = clients(root.resolve("missing"), true);

            JeffreyException failure = assertThrows(JeffreyException.class, () -> clients.discovery().info());
            assertEquals(ErrorCode.CONNECTOR_UNAVAILABLE, failure.getCode());
        }

        /**
         * Reachability is the probe's to report; a listing that cannot be read is nothing there, as on
         * a share (shared/volume's {@code VolumeDirectories}).
         */
        @Test
        void aFileInPlaceOfTheDirectoryIsUnavailable() throws IOException {
            Path file = Files.writeString(root.resolve("file.txt"), "not a directory");
            ConnectorClients clients = clients(file, true);

            JeffreyException failure = assertThrows(JeffreyException.class, () -> clients.discovery().info());
            assertEquals(ErrorCode.CONNECTOR_UNAVAILABLE, failure.getCode());
            assertEquals(List.of(), clients.discovery().allWorkspaces());
        }

        /** Looked at on every call: a directory created after opening is read from then on. */
        @Test
        void aDirectoryCreatedLaterIsReadFromThenOn() throws IOException {
            Path later = root.resolve("later");
            ConnectorClients clients = clients(later, true);
            assertThrows(JeffreyException.class, () -> clients.discovery().info());
            assertEquals(List.of(), clients.discovery().allWorkspaces());

            Files.createDirectories(later.resolve("staging"));

            assertEquals(List.of("staging"), clients.discovery().allWorkspaces().stream().map(WorkspaceInfo::id).toList());
        }
    }

    @Nested
    class Reading {

        @Test
        void servesTheTreeAsWorkspacesProjectsAndSessions() {
            ConnectorClients clients = clients(true);

            assertEquals(List.of("prod"), clients.discovery().allWorkspaces().stream().map(WorkspaceInfo::id).toList());
            assertEquals(List.of("orders"),
                    clients.discovery().allProjects("prod").stream().map(ConnectorProjectResponse::id).toList());
            List<RecordingSessionResponse> sessions = clients.repository().recordingSessions(ORDERS);
            assertEquals(List.of(VolumeWriter.sessionIdAt(T0)), sessions.stream().map(RecordingSessionResponse::id).toList());
        }

        /** Nothing is remembered between calls: a session the profiler starts shows up on the next one. */
        @Test
        void seesASessionStartedAfterTheFirstRead() {
            ConnectorClients clients = clients(true);
            assertEquals(1, clients.repository().recordingSessions(ORDERS).size());

            VolumeWriter.file(VolumeWriter.session(instance, T0.plusSeconds(600)), "rec-20260220-121500+0000.jfr", "chunk");

            assertEquals(2, clients.repository().recordingSessions(ORDERS).size());
        }
    }

    @Nested
    class Changing {

        @Test
        void deletesAProjectWhenWritable() {
            clients(false).projects().deleteProject(ORDERS);

            assertTrue(Files.notExists(root.resolve("prod").resolve("orders")));
        }

        @Test
        void refusesEveryChangeWhenReadOnly() {
            ConnectorClients clients = clients(true);

            assertThrows(JeffreyClientException.class, () -> clients.projects().deleteProject(ORDERS));
            assertTrue(Files.exists(root.resolve("prod").resolve("orders")));
        }
    }
}
