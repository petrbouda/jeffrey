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

package cafe.jeffrey.microscope.core.manager.recordings;

import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.Recording;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RecordingsManager {

    // Group operations
    String createGroup(String groupName);

    List<RecordingGroup> listGroups();

    void deleteGroup(String groupId);

    // Recording operations
    String uploadRecording(String filename, InputStream inputStream, String groupId);

    /**
     * Persist a recording downloaded from a project session. The merged recording file
     * and any artifact files (heap dumps, logs) are moved into QA storage and recorded
     * with {@code project_id = NULL}; {@code originTags} are written to {@code recording_tags}
     * so the recording can be traced back to its source.
     *
     * @param recordingName name to attach to the new recording row
     * @param mergedRecordingFile path to the merged JFR file (in a temp dir owned by the caller)
     * @param artifactFiles heap dumps / logs / etc. associated with the same session
     * @param originTags system tags identifying the source (typically {@code origin.*} keys)
     * @return the newly created QA recording id
     */
    String createDownloadedRecording(
            String recordingName,
            Path mergedRecordingFile,
            List<Path> artifactFiles,
            Map<String, String> originTags);

    void moveRecordingToGroup(String recordingId, String groupId);

    List<Recording> listRecordings();

    /**
     * Returns tags for many recordings in a single query.
     * Recordings with no tags do not appear in the result map.
     */
    Map<String, List<RecordingTag>> tagsForRecordings(java.util.Collection<String> recordingIds);

    void deleteRecording(String recordingId);

    // Profile operations
    String analyzeRecording(String recordingId);

    void updateProfileName(String profileId, String profileName);

    void deleteProfile(String recordingId);

    Optional<ProfileManager> profile(String profileId);
}
