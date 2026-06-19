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

package cafe.jeffrey.shared.ui.workspace.dto;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider.ProfileInfo;

import java.util.List;

/**
 * Recording list item shared between deployments. The {@code profile*} fields are populated from
 * the deployment's {@link cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider}
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
        String profileName,
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
                recording.profileName(),
                fileResponses,
                tags.stream().map(RecordingTagResponse::from).toList());
    }
}
