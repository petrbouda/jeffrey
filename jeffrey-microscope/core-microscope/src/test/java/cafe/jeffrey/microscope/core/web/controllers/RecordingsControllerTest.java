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

package cafe.jeffrey.microscope.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.shared.ui.remote.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.storage.recording.api.file.Bundle;
import cafe.jeffrey.storage.recording.api.file.BundleFile;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.shared.ui.remote.controller.RecordingsController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class RecordingsControllerTest {

    /** The recordings here have no profile to report. */
    private static final RecordingProfileInfoProvider NO_PROFILES =
            recording -> List.of();

    @Mock
    RecordingsManager recordingsManager;

    @Test
    void createsGroup() {
        when(recordingsManager.createGroup("My Group")).thenReturn("group-1");

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

        assertThat(mvc.post().uri("/api/internal/recordings/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"My Group"}"""))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.groupId").asString().isEqualTo("group-1");
    }

    @Test
    void rejectsBlankGroupName() {
        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

        assertThat(mvc.post().uri("/api/internal/recordings/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":""}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").asString().isEqualTo("Group name is required");
    }

    @Test
    void importsFromPath() {
        when(recordingsManager.importRecordingFromPath(any())).thenReturn("rec-1");

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

        assertThat(mvc.post().uri("/api/internal/recordings/from-path")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"path":"/tmp/recording.jfr"}"""))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.recordingId").asString().isEqualTo("rec-1");
    }

    /**
     * The import says what kind of recording it was, so the caller can open the profile where that
     * kind lands — a heap dump on its overview, not on the JFR dashboard.
     */
    @Test
    void importFromPathTellsTheRecordingsEventSource() {
        when(recordingsManager.importRecordingFromPath(any())).thenReturn("rec-1");
        when(recordingsManager.findRecording("rec-1")).thenReturn(Optional.of(new Recording(
                "rec-1", "dump.hprof", null, RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, null, null, false, null, null, List.of())));

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

        assertThat(mvc.post().uri("/api/internal/recordings/from-path")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"path":"/tmp/dump.hprof"}"""))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.eventSource").asString().isEqualTo("HEAP_DUMP");
    }

    @Test
    void rejectsBlankPath() {
        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

        assertThat(mvc.post().uri("/api/internal/recordings/from-path")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"path":"  "}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").asString().isEqualTo("Path is required");
    }

    @Test
    void listsRecordings() {
        when(recordingsManager.listRecordings()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

        assertThat(mvc.get().uri("/api/internal/recordings/recordings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Nested
    class Bundles {

        private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

        private Recording member(String id, String bundleId, String groupId) {
            return new Recording(id, id, groupId, RecordingEventSource.JDK, NOW, NOW, NOW,
                    false, null, null, List.of(), bundleId);
        }

        @Test
        void listsABundleWithItsFilesAndTheOriginItsItemsCarry() {
            BundleFile log = new BundleFile("bf-1", "bundle-1", "gc.jvm-log", ManagedFile.JVM_LOG, NOW, 2048L);
            when(recordingsManager.listBundles()).thenReturn(List.of(
                    new Bundle("bundle-1", "checkout", "group-1", NOW, List.of(log))));
            when(recordingsManager.listRecordings()).thenReturn(List.of(member("rec-1", "bundle-1", null)));
            when(recordingsManager.tagsForRecordings(any())).thenReturn(Map.of(
                    "rec-1", List.of(new RecordingTag("origin.remote", "Production"))));

            MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

            assertThat(mvc.get().uri("/api/internal/recordings/bundles"))
                    .hasStatusOk()
                    .bodyJson()
                    .satisfies(json -> {
                        assertThat(json).extractingPath("$[0].name").asString().isEqualTo("checkout");
                        assertThat(json).extractingPath("$[0].groupId").asString().isEqualTo("group-1");
                        assertThat(json).extractingPath("$[0].files[0].filename").asString().isEqualTo("gc.jvm-log");
                        assertThat(json).extractingPath("$[0].tags[0].value").asString().isEqualTo("Production");
                    });
        }

        /** A group holds standalone recordings and bundles; a bundle counts once, whatever it holds. */
        @Test
        void countsABundleOnceInItsGroup() {
            when(recordingsManager.listGroups()).thenReturn(List.of(new RecordingGroup("group-1", "Prod", NOW)));
            when(recordingsManager.listRecordings()).thenReturn(List.of(
                    member("rec-solo", null, "group-1"),
                    member("rec-a", "bundle-1", null),
                    member("rec-b", "bundle-1", null)));
            when(recordingsManager.listBundles()).thenReturn(List.of(
                    new Bundle("bundle-1", "checkout", "group-1", NOW, List.of())));

            MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

            assertThat(mvc.get().uri("/api/internal/recordings/groups"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$[0].recordingCount").asNumber().isEqualTo(2);
        }

        @Test
        void movesABundleToAGroup() {
            MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

            assertThat(mvc.put().uri("/api/internal/recordings/bundles/bundle-1/group")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"groupId":"group-2"}"""))
                    .hasStatusOk();
            verify(recordingsManager).moveBundleToGroup("bundle-1", "group-2");
        }

        @Test
        void deletesABundle() {
            MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

            assertThat(mvc.delete().uri("/api/internal/recordings/bundles/bundle-1")).hasStatusOk();
            verify(recordingsManager).deleteBundle("bundle-1");
        }

        /** Saved under the name the file is known by: the storage prefix in front of it is not part of it. */
        @Test
        void downloadsABundleFileUnderItsOwnName(@TempDir Path dir) throws IOException {
            Path log = Files.writeString(dir.resolve("bundle-1-gc.jvm-log"), "[0.001s][info][gc] Using G1");
            when(recordingsManager.findBundleFile("bundle-1", "bf-1")).thenReturn(Optional.of(log));
            when(recordingsManager.findBundle("bundle-1")).thenReturn(Optional.of(bundleWithFile("gc.jvm-log")));

            ResponseEntity<Resource> response = new RecordingsController(
                    recordingsManager, NO_PROFILES).downloadBundleFile("bundle-1", "bf-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(savedAs(response)).isEqualTo("gc.jvm-log");
            assertThat(response.getBody().getContentAsString(StandardCharsets.UTF_8))
                    .isEqualTo("[0.001s][info][gc] Using G1");
        }

        /** A name with a quote or outside ASCII arrives whole instead of breaking the header. */
        @Test
        void aNameOutsideAsciiOrWithAQuoteSurvivesTheHeader(@TempDir Path dir) throws IOException {
            String name = "zahlungsdienst-\"ü\".jvm-log";
            Path log = Files.writeString(dir.resolve("bundle-1-log"), "log");
            when(recordingsManager.findBundleFile("bundle-1", "bf-1")).thenReturn(Optional.of(log));
            when(recordingsManager.findBundle("bundle-1")).thenReturn(Optional.of(bundleWithFile(name)));

            ResponseEntity<Resource> response = new RecordingsController(
                    recordingsManager, NO_PROFILES).downloadBundleFile("bundle-1", "bf-1");

            assertThat(savedAs(response)).isEqualTo(name);
        }

        @Test
        void downloadsARecordingFileUnderItsOwnName(@TempDir Path dir) throws IOException {
            Path chunk = Files.writeString(dir.resolve("rec-1-rec-20260220-120500.jfr"), "chunk");
            when(recordingsManager.findRecordingFile("rec-1", "rf-1")).thenReturn(Optional.of(chunk));
            when(recordingsManager.findRecording("rec-1")).thenReturn(Optional.of(new Recording(
                    "rec-1", "checkout", null, RecordingEventSource.JDK, Instant.EPOCH, null, null,
                    false, null, null, List.of(new RecordingFile(
                            "rf-1", "rec-1", "rec-20260220-120500.jfr", ManagedFile.JFR, Instant.EPOCH, 5L)))));

            ResponseEntity<Resource> response = new RecordingsController(
                    recordingsManager, NO_PROFILES).downloadFile("rec-1", "rf-1");

            assertThat(savedAs(response)).isEqualTo("rec-20260220-120500.jfr");
        }

        private static Bundle bundleWithFile(String filename) {
            return new Bundle("bundle-1", "checkout", null, Instant.EPOCH, List.of(
                    new BundleFile("bf-1", "bundle-1", filename, ManagedFile.of(filename), Instant.EPOCH, 3L)));
        }

        private static String savedAs(ResponseEntity<Resource> response) {
            return ContentDisposition.parse(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .getFilename();
        }

        @Test
        void refusesAFileTheBundleDoesNotHold() {
            when(recordingsManager.findBundleFile("bundle-1", "nope")).thenReturn(Optional.empty());

            MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, NO_PROFILES));

            assertThat(mvc.get().uri("/api/internal/recordings/bundles/bundle-1/files/nope/download"))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }
}
