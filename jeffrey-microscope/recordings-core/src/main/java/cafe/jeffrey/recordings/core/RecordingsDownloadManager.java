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

package cafe.jeffrey.recordings.core;

import cafe.jeffrey.microscope.model.repository.ChunkWindow;

import java.util.List;

public interface RecordingsDownloadManager {

    /**
     * Downloads every finished file of the session and stores it as one local recording.
     *
     * @param recordingSessionId the upstream session to download
     * @return id of the recording created in the local store, so the caller can go on to
     * analyse it without having to search the store for whatever appeared last
     */
    String downloadSession(String recordingSessionId);

    /**
     * Downloads the named files of the session and stores them as one local recording.
     *
     * @param recordingSessionId the upstream session to download
     * @param rawRecordingIds    ids of the files to take from that session
     * @return id of the recording created in the local store
     */
    String downloadRecordings(String recordingSessionId, List<String> rawRecordingIds);

    /**
     * Downloads the recording files of the session that cover the window and stores them as one
     * local recording, named and tagged with the span those files actually cover. Nothing else the
     * session holds comes with them: an artifact is fetched on its own.
     *
     * @param recordingSessionId the upstream session to take the window from
     * @param window             the span of interest; the files straddling its bounds are included
     * @return id of the recording created in the local store
     * @throws IllegalArgumentException when no finished recording file of the session touches the window
     */
    String downloadWindow(String recordingSessionId, ChunkWindow window);
}
