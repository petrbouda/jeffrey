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

package cafe.jeffrey.microscope.volume.azure;

import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.hub.client.ProjectScope;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.hub.AzureFilesAddress;
import cafe.jeffrey.microscope.model.hub.AzureFilesAuth;
import cafe.jeffrey.microscope.model.hub.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.volume.client.VolumeHubClients;
import cafe.jeffrey.microscope.volume.client.VolumeSource;
import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import cafe.jeffrey.shared.volume.liveness.HeartbeatReader;
import cafe.jeffrey.shared.volume.liveness.SessionDeadlines;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Microscope reading a hub's volume straight off an NFS Azure file share — the protocol whose
 * listings carry no timestamps — through the same clients that would otherwise talk to the hub.
 */
class AzureFilesSourceTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);
    private static final String SESSION = "prod/orders/pod-a/s-live/";
    private static final ProjectScope ORDERS = new ProjectScope("prod", "p-orders");

    private HubClients clients;

    @BeforeEach
    void setUp() {
        StubFileShare share = new StubFileShare(AzureFilesProtocol.NFS)
                .file("prod/orders/" + JeffreyLayout.PROJECT_INFO_FILE,
                        Json.toString(new RemoteProject("p-orders", "orders", T0.toEpochMilli(), Map.of())), T0)
                .file("prod/orders/pod-a/" + JeffreyLayout.INSTANCE_INFO_FILE,
                        Json.toString(new RemoteProjectInstance("pod-a", T0.toEpochMilli())), T0)
                .file(SESSION + JeffreyLayout.SESSION_INFO_FILE,
                        Json.toString(new RemoteProjectInstanceSession("s-live", T0.toEpochMilli(), "Europe/Prague")), T0)
                .file(SESSION + HeartbeatConstants.HEARTBEAT_DIR + "/" + HeartbeatConstants.HEARTBEAT_FILE,
                        Long.toString(NOW.toEpochMilli()), NOW)
                .file(SESSION + "profile-20260220-131000.jfr", "closed chunk", T0)
                .file(SESSION + "profile-20260220-131500.jfr", "open", T0)
                .file(SESSION + "gc.log", "gc", T0);

        AzureFilesVolume volume = AzureFilesVolumes.open(
                new AzureFilesAddress("https://acct.file.core.windows.net", StubFileShare.shareName(), "",
                        AzureFilesProtocol.NFS, AzureFilesAuth.SAS),
                new AzureFilesCredential.Sas("sv=2026-06-06&sig=c2ln"),
                share);
        SessionLiveness liveness = new SessionLiveness(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new HeartbeatReader(volume),
                new SessionDeadlines(Duration.ofSeconds(10), Duration.ofMinutes(1)));
        clients = VolumeHubClients.of(new VolumeSource(volume, liveness, "azure-files"));
    }

    @Test
    void aLiveSessionReadsWithItsChunksTimedByTheirNamesInTheSessionsZone() {
        RecordingSessionResponse session = clients.repository().recordingSession(ORDERS, "s-live");

        assertEquals(RecordingStatus.ACTIVE, session.status());
        List<RepositoryFileResponse> recordings = session.files().stream()
                .filter(RepositoryFileResponse::isRecording)
                .toList();
        assertEquals(List.of(
                        Instant.parse("2026-02-20T12:15:00Z").toEpochMilli(),
                        Instant.parse("2026-02-20T12:10:00Z").toEpochMilli()),
                recordings.stream().map(RepositoryFileResponse::createdAt).toList());
        assertEquals(List.of(RecordingStatus.ACTIVE, RecordingStatus.FINISHED),
                recordings.stream().map(RepositoryFileResponse::status).toList());
        assertEquals(3, session.files().size(), "the log is listed too, timed by one look at it");
    }

    @Test
    void aClosedChunkDownloadsWhileTheOpenOneIsRefused() {
        StringBuilder received = new StringBuilder();
        clients.files().streamFile(ORDERS, "s-live", "profile-20260220-131000",
                (in, file) -> received.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)));

        assertEquals("closed chunk", received.toString());
        assertEquals(1, clients.discovery().allProjects("prod").size());
    }
}
