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

package cafe.jeffrey.microscope.core.manager.qanalysis;

import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.Recording;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface QuickAnalysisManager {

    // Group operations
    String createGroup(String groupName);

    List<RecordingGroup> listGroups();

    void deleteGroup(String groupId);

    // Recording operations
    String uploadRecording(String filename, InputStream inputStream, String groupId);

    void moveRecordingToGroup(String recordingId, String groupId);

    List<Recording> listRecordings();

    void deleteRecording(String recordingId);

    // Profile operations
    String analyzeRecording(String recordingId);

    void updateProfileName(String profileId, String profileName);

    void deleteProfile(String recordingId);

    Optional<ProfileManager> profile(String profileId);
}
