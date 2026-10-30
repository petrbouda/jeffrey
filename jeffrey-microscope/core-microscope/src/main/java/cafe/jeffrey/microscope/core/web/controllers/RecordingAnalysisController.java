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

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.recordings.IdeRecordingLookup;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.web.dto.response.AnalyzeResponse;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.nio.file.Path;

/**
 * Microscope-only profile-lifecycle endpoints layered on the shared recordings store. The store
 * operations (groups / upload / list / download / delete) live in the shared
 * {@code cafe.jeffrey.shared.ui.remote.controller.RecordingsController}; the profile-mutating
 * operations below depend on the microscope-specific {@link RecordingsManager} and therefore stay
 * in microscope. Shares the {@code /api/internal/recordings} base path with different methods.
 */
@RestController
@RequestMapping("/api/internal/recordings")
public class RecordingAnalysisController {

    private final RecordingsManager recordingsManager;
    private final IdeRecordingLookup ideRecordingLookup;

    public RecordingAnalysisController(
            RecordingsManager recordingsManager,
            IdeRecordingLookup ideRecordingLookup) {

        this.recordingsManager = recordingsManager;
        this.ideRecordingLookup = ideRecordingLookup;
    }

    /**
     * What Microscope holds for a recording file the caller has on disk, addressed by its absolute
     * path. Built for the IntelliJ plugin's recording panel, which opens on a file and has to say
     * whether it has been analysed before offering to analyse it.
     *
     * <p>Read-only: a path Microscope has never seen answers {@code NOT_IMPORTED} rather than being
     * imported as a side effect of being asked about. Importing stays an explicit
     * {@code POST /api/internal/recordings/from-path} — served by {@code RecordingsController} in the
     * workspaces module, not by this one, though the two share a base path — because it copies the
     * file into Microscope's storage.
     */
    @GetMapping(value = "/by-path", produces = MediaType.APPLICATION_JSON_VALUE)
    public IdeRecordingStateResponse byPath(
            @RequestParam("path") String path,
            @RequestParam(value = "sizeInBytes", required = false, defaultValue = "0") long sizeInBytes) {

        if (path == null || path.isBlank()) {
            throw Exceptions.invalidRequest("Path is required");
        }
        return ideRecordingLookup.byPath(Path.of(path.trim()), sizeInBytes);
    }

    @PostMapping(value = "/recordings/{recordingId}/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse analyzeRecording(@PathVariable("recordingId") String recordingId) {
        String profileId = recordingsManager.analyzeRecording(recordingId);
        return new AnalyzeResponse(profileId);
    }

    /**
     * Builds another profile from the recording beside the ones it already has. A recording's first
     * initialization, and a retry of a failed one, go through {@code /analyze} instead.
     */
    @PostMapping(value = "/recordings/{recordingId}/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse initializeAgain(@PathVariable("recordingId") String recordingId) {
        String profileId = recordingsManager.initializeAgain(recordingId);
        return new AnalyzeResponse(profileId);
    }

    @PutMapping(value = "/recordings/{recordingId}/profiles/{profileId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void updateProfile(
            @PathVariable("recordingId") String recordingId,
            @PathVariable("profileId") String profileId,
            @RequestBody UpdateProfileRequest request) {

        if (request == null || request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Profile name is required");
        }

        boolean ownsProfile = recordingsManager.profiles(recordingId).stream()
                .anyMatch(profile -> profile.id().equals(profileId));
        if (!ownsProfile) {
            throw Exceptions.invalidRequest(
                    "Recording has no such profile: recordingId=%s profileId=%s".formatted(recordingId, profileId));
        }

        recordingsManager.updateProfileName(profileId, request.name().trim());
    }

    @DeleteMapping("/recordings/{recordingId}/profiles/{profileId}")
    public void deleteProfile(
            @PathVariable("recordingId") String recordingId,
            @PathVariable("profileId") String profileId) {

        recordingsManager.deleteProfile(recordingId, profileId);
    }

    public record UpdateProfileRequest(String name) {
    }
}
