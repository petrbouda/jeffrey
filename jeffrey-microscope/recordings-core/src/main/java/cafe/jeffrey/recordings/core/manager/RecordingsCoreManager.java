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

package cafe.jeffrey.recordings.core.manager;

import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.storage.recording.api.file.Recording;

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
            List<Path> recordingFiles,
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
