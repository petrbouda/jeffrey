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

package pbouda.jeffrey.local.core.resources.response;

import pbouda.jeffrey.local.persistence.model.QuickRecordingInfo;
import pbouda.jeffrey.shared.common.InstantUtils;

public record QuickRecordingResponse(
        String id,
        String filename,
        String groupId,
        String eventSource,
        long sizeInBytes,
        String uploadedAt,
        long durationInMillis,
        String profileId,
        boolean hasProfile,
        long profileSizeInBytes) {

    public static QuickRecordingResponse from(QuickRecordingInfo info, long profileSizeInBytes) {
        return new QuickRecordingResponse(
                info.recordingId(),
                info.filename(),
                info.groupId(),
                info.eventSource().name(),
                info.sizeInBytes(),
                InstantUtils.formatInstant(info.uploadedAt()),
                info.duration().toMillis(),
                info.profileId(),
                info.hasProfile(),
                profileSizeInBytes);
    }
}
