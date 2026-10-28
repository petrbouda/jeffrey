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

import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.volume.client.VolumeRemoteClients;
import cafe.jeffrey.microscope.volume.client.VolumeSource;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Microscope reading a hub's volume straight off an NFS Azure file share — the protocol whose
 * listings carry no timestamps — through the same clients that would otherwise talk to the hub.
 */
class AzureFilesSourceTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);
    private static final String LIVE = VolumeWriter.sessionIdAt(T0);
    private static final String SESSION = "prod/orders/pod-a/" + LIVE + "/";
    private static final ProjectScope ORDERS = new ProjectScope("prod", "orders");
    /** Chunks a JVM in Prague opened at 12:10 and 12:15 UTC: named in its local time, with its zone. */
    private static final String OLDER_CHUNK = "rec-20260220-131000+0100";
    private static final String NEWEST_CHUNK = "rec-20260220-131500+0100";

    private StubFileShare share;
    private RemoteClients clients;

    @BeforeEach
    void setUp() {
        share = new StubFileShare(AzureFilesProtocol.NFS)
                .file(SESSION + OLDER_CHUNK + ".jfr", "older chunk", T0)
                .file(SESSION + NEWEST_CHUNK + ".jfr", "newest", T0)
                .file(SESSION + "gc.log", "gc", T0);

        AzureFilesVolume volume = AzureFilesVolumes.open(
                new AzureFilesAddress("https://acct.file.core.windows.net", StubFileShare.shareName(), "",
                        AzureFilesProtocol.NFS, AzureFilesAuth.SAS, false),
                new AzureFilesCredential.Sas("sv=2026-06-06&sig=c2ln"),
                share);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        clients = VolumeRemoteClients.of(new VolumeSource(volume, clock, "azure-files", false));
    }

    /** A delete reaches the share as the layout's own write, and reads back as the hub would. */
    @Test
    void aSessionIsDeletedWithItsInstanceOnTheShare() {
        clients.repository().deleteSession(ORDERS, LIVE);

        assertTrue(share.paths().stream().noneMatch(path -> path.startsWith("prod/orders/pod-a")), share.paths().toString());
        assertTrue(share.holds("prod/orders"));
    }

    @Test
    void theNewestSessionReadsWithItsChunksTimedByTheirNamesInTheirZone() {
        RecordingSessionResponse session = clients.repository().recordingSession(ORDERS, LIVE);

        assertTrue(session.newest());
        List<RepositoryFileResponse> recordings = session.files().stream()
                .filter(RepositoryFileResponse::isRecording)
                .toList();
        assertEquals(List.of(
                        Instant.parse("2026-02-20T12:15:00Z").toEpochMilli(),
                        Instant.parse("2026-02-20T12:10:00Z").toEpochMilli()),
                recordings.stream().map(RepositoryFileResponse::createdAt).toList());
        assertEquals(List.of(true, false),
                recordings.stream().map(RepositoryFileResponse::newestRecording).toList());
        assertEquals(3, session.files().size(), "the log is listed too, timed by one look at it");
    }

    @Test
    void everyChunkDownloadsTheNewestToo() {
        StringBuilder received = new StringBuilder();
        clients.files().streamFile(ORDERS, LIVE, OLDER_CHUNK,
                (in, file) -> received.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        clients.files().streamFile(ORDERS, LIVE, NEWEST_CHUNK,
                (in, file) -> received.append('+').append(new String(in.readAllBytes(), StandardCharsets.UTF_8)));

        assertEquals("older chunk+newest", received.toString());
        assertEquals(1, clients.discovery().allProjects("prod").size());
    }
}
