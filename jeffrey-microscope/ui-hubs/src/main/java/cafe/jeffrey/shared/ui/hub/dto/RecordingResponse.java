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

package cafe.jeffrey.shared.ui.hub.dto;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.shared.ui.hub.bridge.ProfileInitProgress;
import cafe.jeffrey.shared.ui.hub.bridge.RecordingProfileInfoProvider.ProfileInfo;

import java.util.List;

/**
 * Recording list item shared between deployments. The {@code profile*} fields are populated from
 * the deployment's {@link cafe.jeffrey.shared.ui.hub.bridge.RecordingProfileInfoProvider}
 * (profile-capable deployments supply real values; others report {@link ProfileInfo#NONE}).
 */
public record RecordingResponse(
        String id,
        String filename,
        String groupId,
        String eventSource,
        long sizeInBytes,
        long uploadedAt,
        long durationInMillis,
        String profileId,
        boolean hasProfile,
        long profileSizeInBytes,
        boolean profileModified,
        long profileCreatedAt,
        String profileName,
        ProfileInitProgress initProgress,
        List<RecordingFileResponse> files,
        List<RecordingTagResponse> tags) {

    public static RecordingResponse from(
            Recording recording,
            ProfileInfo profileInfo,
            List<RecordingTag> tags) {

        RecordingFile primary = recording.files().isEmpty() ? null : recording.files().getFirst();

        List<RecordingFileResponse> fileResponses = recording.files().stream()
                .map(f -> new RecordingFileResponse(
                        f.id(),
                        f.filename(),
                        f.sizeInBytes(),
                        f.recordingFileType().name(),
                        f.recordingFileType().description()))
                .toList();

        return new RecordingResponse(
                recording.id(),
                primary != null ? primary.filename() : recording.recordingName(),
                recording.groupId(),
                recording.eventSource().name(),
                primary != null ? primary.sizeInBytes() : 0,
                recording.createdAt().toEpochMilli(),
                recording.recordingDuration().toMillis(),
                recording.profileId(),
                recording.hasProfile(),
                profileInfo.profileSizeInBytes(),
                profileInfo.profileModified(),
                profileInfo.profileCreatedAt(),
                recording.profileName(),
                profileInfo.initProgress(),
                fileResponses,
                tags.stream().map(RecordingTagResponse::from).toList());
    }
}
