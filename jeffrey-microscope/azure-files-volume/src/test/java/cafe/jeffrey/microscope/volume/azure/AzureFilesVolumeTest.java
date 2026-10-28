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

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import com.azure.core.http.HttpRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The storage port over the Azure Files REST API, driven through the SDK against a share that
 * answers in the service's own shapes — the SMB listing with its timestamps, the NFS one without.
 */
class AzureFilesVolumeTest {

    private static final Instant CREATED = Instant.parse("2026-02-20T12:00:00Z");
    private static final String ENDPOINT = "https://acct.file.core.windows.net";
    private static final String SAS = "sv=2026-06-06&ss=f&srt=sco&sp=rl&sig=c2lnbmF0dXJl";
    private static final VolumePath ROOT = VolumePath.root();
    private static final VolumePath SESSION = ROOT.resolve("prod").resolve("orders").resolve("pod-a").resolve("s-1");

    private static AzureFilesVolume volume(StubFileShare share, AzureFilesProtocol protocol, String rootPath) {
        AzureFilesAddress address = new AzureFilesAddress(
                ENDPOINT, StubFileShare.shareName(), rootPath, protocol, AzureFilesAuth.SAS, false);
        return AzureFilesVolumes.open(address, new AzureFilesCredential.Sas(SAS), share);
    }

    private static StubFileShare sessionOn(AzureFilesProtocol protocol, String rootPrefix) {
        return new StubFileShare(protocol)
                .file(rootPrefix + "prod/orders/pod-a/s-1/rec-20260220-120000.jfr", "chunk one", CREATED)
                .file(rootPrefix + "prod/orders/pod-a/s-1/gc.log", "gc", CREATED.plusSeconds(5))
                .directory(rootPrefix + "prod/orders/pod-a/s-1/.heartbeat", CREATED);
    }

    @Nested
    class OnAnSmbShare {

        private final StubFileShare share = sessionOn(AzureFilesProtocol.SMB, "");
        private final VolumeAccess volume = volume(share, AzureFilesProtocol.SMB, "");

        /** One listing call answers every entry's kind, size and creation time. */
        @Test
        void listsEachEntryWithItsTimesFromOneCall() {
            List<VolumeEntry> entries = volume.list(SESSION);

            assertEquals(List.of(".heartbeat", "gc.log", "rec-20260220-120000.jfr"),
                    entries.stream().map(VolumeEntry::name).sorted().toList());
            VolumeEntry chunk = entries.stream().filter(entry -> entry.name().endsWith(".jfr")).findFirst().orElseThrow();
            assertTrue(chunk.isFile());
            assertEquals("chunk one".length(), chunk.size());
            assertEquals(Optional.of(CREATED), chunk.createdAt());
            assertTrue(entries.stream().filter(entry -> entry.name().equals(".heartbeat")).findFirst().orElseThrow().isDirectory());
        }

        @Test
        void aLookAtOneEntryTellsAFileFromADirectoryAndKeepsItsCreationTime() {
            VolumeEntry log = volume.stat(SESSION.resolve("gc.log")).orElseThrow();

            assertTrue(log.isFile());
            assertEquals(Optional.of(CREATED.plusSeconds(5)), log.createdAt());
            assertTrue(volume.stat(SESSION).orElseThrow().isDirectory());
            assertTrue(volume.stat(ROOT).orElseThrow().isDirectory());
        }

        @Test
        void readsAndStreamsAFile() throws IOException {
            assertArrayEquals("gc".getBytes(StandardCharsets.UTF_8), volume.read(SESSION.resolve("gc.log")).orElseThrow());

            try (VolumeAccess.OpenedFile chunk = volume.open(SESSION.resolve("rec-20260220-120000.jfr")).orElseThrow()) {
                assertEquals("rec-20260220-120000.jfr", chunk.name());
                assertEquals("chunk one".length(), chunk.size());
                assertEquals("chunk one", new String(chunk.content().readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        /** A marker larger than one can be is refused after one byte past the cap, never fetched whole. */
        @Test
        void refusesAFileLargerThanAMarkerCanBe() {
            share.file("prod/orders/pod-a/s-1/.session-info.json", "x".repeat(VolumeAccess.MAX_READ_BYTES + 10), CREATED);

            assertThrows(UncheckedIOException.class, () -> volume.read(SESSION.resolve(".session-info.json")));
            String range = share.requests().getLast().getHeaders().getValue("x-ms-range");
            assertEquals("bytes=0-" + VolumeAccess.MAX_READ_BYTES, range);
        }

        @Test
        void readsAnEmptyFileAsEmpty() {
            share.file("prod/orders/pod-a/s-1/.retained", "", CREATED);

            assertArrayEquals(new byte[0], volume.read(SESSION.resolve(".retained")).orElseThrow());
        }

        @Test
        void signsEveryRequestWithTheSas() {
            volume.list(SESSION);

            assertTrue(share.requests().stream().map(HttpRequest::getUrl)
                    .allMatch(url -> url.getQuery() != null && url.getQuery().contains("sig=")));
        }
    }

    @Nested
    class Writing {

        private final StubFileShare share = sessionOn(AzureFilesProtocol.SMB, "volume/");
        private final WritableVolume volume = volume(share, AzureFilesProtocol.SMB, "volume");

        @Test
        void createsEveryMissingDirectoryUnderTheRoot() {
            volume.createDirectories(ROOT.resolve("staging").resolve("checkout"));

            assertTrue(share.holds("volume/staging"));
            assertTrue(share.holds("volume/staging/checkout"));
        }

        /** Through a scratch file renamed onto the marker, so no reader alongside sees half of one. */
        @Test
        void writesAMarkerWholeAndLeavesNoScratchFileBehind() {
            volume.createDirectories(ROOT.resolve("staging"));

            volume.write(ROOT.resolve("staging").resolve(".workspace-info.json"), "{\"name\":\"Staging\"}".getBytes(StandardCharsets.UTF_8));
            volume.write(ROOT.resolve("staging").resolve(".workspace-info.json"), "{\"name\":\"Renamed\"}".getBytes(StandardCharsets.UTF_8));

            assertEquals("{\"name\":\"Renamed\"}", share.content("volume/staging/.workspace-info.json"));
            assertTrue(share.paths().stream().noneMatch(path -> path.endsWith(".tmp")), share.paths().toString());
            assertTrue(share.requests().stream().anyMatch(request -> request.getUrl().getQuery().contains("comp=rename")));
        }

        @Test
        void createsAnEmptyFlagInOneCall() {
            volume.write(SESSION.resolve(".retained"), new byte[0]);

            assertEquals("", share.content("volume/prod/orders/pod-a/s-1/.retained"));
        }

        @Test
        void deletesADirectoryWithEverythingUnderIt() {
            volume.delete(SESSION);

            assertTrue(share.paths().stream().noneMatch(path -> path.startsWith("volume/prod/orders/pod-a/s-1")),
                    share.paths().toString());
            assertTrue(share.holds("volume/prod/orders/pod-a"));
        }

        @Test
        void deletesAFileAndTreatsWhatIsGoneAsDone() {
            volume.delete(SESSION.resolve("gc.log"));
            volume.delete(SESSION.resolve("gc.log"));

            assertFalse(share.holds("volume/prod/orders/pod-a/s-1/gc.log"));
        }

        @Test
        void aWriteTheCredentialRefusesSaysWhichRightIsMissing() {
            share.rejectEveryCredential();

            UncheckedIOException refused = assertThrows(UncheckedIOException.class,
                    () -> volume.createDirectories(ROOT.resolve("staging")));
            assertTrue(refused.getMessage().contains("lacks"), refused.getMessage());
        }
    }

    /** An NFS share lists names and sizes alone; a look at one entry answers its last change. */
    @Nested
    class OnAnNfsShare {

        private final StubFileShare share = sessionOn(AzureFilesProtocol.NFS, "");
        private final VolumeAccess volume = volume(share, AzureFilesProtocol.NFS, "");

        @Test
        void listsWithoutAskingForTimestampsTheShareCannotGive() {
            List<VolumeEntry> entries = volume.list(SESSION);

            assertEquals(3, entries.size());
            assertTrue(entries.stream().allMatch(entry -> entry.createdAt().isEmpty()));
            assertEquals("chunk one".length(),
                    entries.stream().filter(entry -> entry.name().endsWith(".jfr")).findFirst().orElseThrow().size());
        }

        @Test
        void aLookAtOneEntryAnswersItsLastChangeForItsTime() {
            assertEquals(Optional.of(CREATED.plusSeconds(35)), volume.stat(SESSION.resolve("gc.log")).orElseThrow().createdAt());
        }
    }

    @Nested
    class NothingThere {

        private final VolumeAccess volume = volume(sessionOn(AzureFilesProtocol.SMB, ""), AzureFilesProtocol.SMB, "");

        @Test
        void isAnEmptyAnswerNeverAFailure() {
            assertEquals(List.of(), volume.list(ROOT.resolve("missing")));
            assertEquals(List.of(), volume.list(SESSION.resolve("gc.log")));
            assertEquals(Optional.empty(), volume.stat(SESSION.resolve("missing.jfr")));
            assertEquals(Optional.empty(), volume.read(SESSION.resolve("missing.jfr")));
            assertEquals(Optional.empty(), volume.open(SESSION.resolve("missing.jfr")));
        }
    }

    /** A rejected credential must not read as an empty volume: that would hide every session. */
    @Test
    void aRefusalOfTheServiceIsAFailureNotAnEmptyVolume() {
        StubFileShare share = sessionOn(AzureFilesProtocol.SMB, "");
        VolumeAccess volume = volume(share, AzureFilesProtocol.SMB, "");
        share.rejectEveryCredential();

        assertThrows(UncheckedIOException.class, () -> volume.list(SESSION));
        assertThrows(UncheckedIOException.class, () -> volume.stat(SESSION.resolve("gc.log")));
        assertThrows(UncheckedIOException.class, () -> volume.read(SESSION.resolve("gc.log")));
    }

    @Test
    void aRootPathPutsTheVolumeInsideTheShare() {
        StubFileShare share = sessionOn(AzureFilesProtocol.SMB, "jeffrey/workspaces/");
        VolumeAccess volume = volume(share, AzureFilesProtocol.SMB, "/jeffrey/workspaces/");

        assertEquals(List.of("prod"), volume.list(ROOT).stream().map(VolumeEntry::name).toList());
        assertFalse(volume.list(SESSION).isEmpty());
    }
}
