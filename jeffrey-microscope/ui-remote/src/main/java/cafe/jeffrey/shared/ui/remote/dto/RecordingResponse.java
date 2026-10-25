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

package cafe.jeffrey.shared.ui.remote.dto;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.shared.ui.remote.bridge.ProfileInitProgress;
import cafe.jeffrey.shared.ui.remote.bridge.RecordingProfileInfoProvider.ProfileInfo;

import java.util.List;

/**
 * Recording list item shared between deployments. The {@code profile*} fields are populated from
 * the deployment's {@link cafe.jeffrey.shared.ui.remote.bridge.RecordingProfileInfoProvider}
 * (profile-capable deployments supply real values; others report {@link ProfileInfo#NONE}).
 *
 * @param filename    the recording's name: the file's own name for an upload, the session's name for
 *                    the recording item of a bundle
 * @param sizeInBytes all of the recording's files together
 * @param bundleId    the bundle the recording came in with, or {@code null} for a standalone one
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
        List<RecordingTagResponse> tags,
        String bundleId) {

    public static RecordingResponse from(
            Recording recording,
            ProfileInfo profileInfo,
            List<RecordingTag> tags) {

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
                recording.recordingName(),
                recording.groupId(),
                recording.eventSource().name(),
                recording.files().stream().mapToLong(RecordingFile::sizeInBytes).sum(),
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
                tags.stream().map(RecordingTagResponse::from).toList(),
                recording.bundleId());
    }
}
