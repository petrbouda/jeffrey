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

package cafe.jeffrey.shared.ui.workspace.bridge;

import cafe.jeffrey.shared.common.model.Recording;

/**
 * Optional enrichment SPI for the shared recordings list. Supplies the per-recording profile
 * fields ({@code profileSizeInBytes} / {@code profileModified}) that only a profile-capable
 * deployment can compute. Deployments without profiles (e.g. the analyst) wire {@link #NOOP},
 * which reports no profile data; microscope provides a real implementation backed by its profile
 * managers.
 */
@FunctionalInterface
public interface RecordingProfileInfoProvider {

    /**
     * Resolves the profile size/modified info for a recording. Implementations should return
     * {@link ProfileInfo#NONE} when the recording has no associated profile.
     */
    ProfileInfo profileInfo(Recording recording);

    record ProfileInfo(long profileSizeInBytes, boolean profileModified) {

        public static final ProfileInfo NONE = new ProfileInfo(0L, false);
    }

    RecordingProfileInfoProvider NOOP = recording -> ProfileInfo.NONE;
}
