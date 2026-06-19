/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.microscope.core.web.controllers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.resources.response.AnalyzeResponse;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Recording;

/**
 * Microscope-only profile-lifecycle endpoints layered on the shared recordings store. The store
 * operations (groups / upload / list / download / delete) live in the shared
 * {@code cafe.jeffrey.shared.ui.workspace.controller.RecordingsController}; the profile-mutating
 * operations below depend on the microscope-specific {@link RecordingsManager} and therefore stay
 * in microscope. Shares the {@code /api/internal/recordings} base path with different methods.
 */
@RestController
@RequestMapping("/api/internal/recordings")
public class RecordingAnalysisController {

    private final RecordingsManager recordingsManager;

    public RecordingAnalysisController(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    @PostMapping(value = "/recordings/{recordingId}/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse analyzeRecording(@PathVariable("recordingId") String recordingId) {
        String profileId = recordingsManager.analyzeRecording(recordingId);
        return new AnalyzeResponse(profileId);
    }

    @PutMapping(value = "/recordings/{recordingId}/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void updateProfile(
            @PathVariable("recordingId") String recordingId,
            @RequestBody UpdateProfileRequest request) {

        if (request == null || request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Profile name is required");
        }

        Recording recording = recordingsManager.listRecordings().stream()
                .filter(r -> r.id().equals(recordingId))
                .findFirst()
                .orElseThrow(() -> Exceptions.invalidRequest("Recording not found: " + recordingId));

        if (!recording.hasProfile()) {
            throw Exceptions.invalidRequest("Recording has no profile: " + recordingId);
        }

        recordingsManager.updateProfileName(recording.profileId(), request.name().trim());
    }

    @DeleteMapping("/recordings/{recordingId}/profile")
    public void deleteProfile(@PathVariable("recordingId") String recordingId) {
        recordingsManager.deleteProfile(recordingId);
    }

    public record UpdateProfileRequest(String name) {
    }
}
