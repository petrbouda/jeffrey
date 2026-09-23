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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.shared.ui.hub.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.shared.ui.hub.controller.RecordingsController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class RecordingsControllerTest {

    @Mock
    RecordingsManager recordingsManager;

    @Test
    void createsGroup() {
        when(recordingsManager.createGroup("My Group")).thenReturn("group-1");

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, RecordingProfileInfoProvider.NOOP));

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
        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, RecordingProfileInfoProvider.NOOP));

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

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, RecordingProfileInfoProvider.NOOP));

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

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, RecordingProfileInfoProvider.NOOP));

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
        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, RecordingProfileInfoProvider.NOOP));

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

        MockMvcTester mvc = mockMvcTesterFor(new RecordingsController(recordingsManager, RecordingProfileInfoProvider.NOOP));

        assertThat(mvc.get().uri("/api/internal/recordings/recordings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }
}
