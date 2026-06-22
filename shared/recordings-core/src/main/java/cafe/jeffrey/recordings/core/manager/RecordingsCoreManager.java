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

package cafe.jeffrey.recordings.core.manager;

import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.shared.common.model.Recording;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deployment-agnostic recording store operations: groups, uploads, downloaded-recording
 * persistence, listing, tagging, deletion and file resolution. Contains NO profile/analysis
 * coupling — profile concerns are layered on top by the deployment (e.g. microscope's
 * {@code ProfileRecordingsManager}) and via the {@link RecordingProfileCleanup} SPI.
 */
public interface RecordingsCoreManager {

    String createGroup(String groupName);

    List<RecordingGroup> listGroups();

    void deleteGroup(String groupId);

    String uploadRecording(String filename, InputStream inputStream, String groupId);

    String importRecordingFromPath(Path path);

    String createDownloadedRecording(
            String recordingName,
            Path mergedRecordingFile,
            List<Path> artifactFiles,
            Map<String, String> originTags);

    void moveRecordingToGroup(String recordingId, String groupId);

    List<Recording> listRecordings();

    /**
     * Resolves a single recording by its (globally unique) id, irrespective of project scope. Unlike
     * {@link #listRecordings()} — which is constrained to the manager's construction-time project scope —
     * this is the project-agnostic by-id read that backs the global per-recording endpoints (file
     * download, AI flamegraph export).
     */
    Optional<Recording> findRecording(String recordingId);

    Map<String, List<RecordingTag>> tagsForRecordings(Collection<String> recordingIds);

    void deleteRecording(String recordingId);

    Optional<Path> findRecordingFile(String recordingId, String fileId);
}
