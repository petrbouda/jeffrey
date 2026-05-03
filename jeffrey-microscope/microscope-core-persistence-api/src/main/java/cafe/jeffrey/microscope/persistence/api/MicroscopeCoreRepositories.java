/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.persistence.api;

import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;

/**
 * Factory interface for platform-level repositories shared between local and server deployments.
 * Provides access to repositories for profiles and recordings.
 */
public interface MicroscopeCoreRepositories {

    ProfileRepository newProfileRepository(String profileId);

    RecordingRepository newRecordingRepository(String projectId);

    RecordingTagsRepository recordingTagsRepository();

    List<ProfileInfo> findAllProfilesByProject(String projectId);

}
