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

package cafe.jeffrey.hub.client.manager;

import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.repository.StreamedFile;

import java.util.List;

public interface RepositoryManager {

    /**
     * Lists every recording session of the project, newest first.
     */
    default List<RecordingSession> listRecordingSessions(boolean withFiles) {
        return listRecordingSessions(withFiles, RecordingSessionFilter.ALL);
    }

    /**
     * Lists the recording sessions that satisfy the filter, newest first. The filter is
     * evaluated by the hub, so the response carries only the sessions asked for.
     */
    List<RecordingSession> listRecordingSessions(boolean withFiles, RecordingSessionFilter filter);

    /**
     * The session as the hub currently has it, files included.
     *
     * @param sessionId the session to fetch
     * @return the session
     * @throws RuntimeException when the hub no longer has it, or cannot be reached
     */
    RecordingSession recordingSession(String sessionId);

    RepositoryStatistics calculateRepositoryStatistics();

    void deleteRecordingSession(String recordingSessionId);

    void deleteFilesInSession(String recordingSessionId, List<String> fileIds);

    /**
     * Marks a recording session as retained, exempting it from every retention job,
     * or releases it again so normal retention resumes.
     *
     * @param recordingSessionId the session to update
     * @param retained           true to exempt the session from retention, false to release it
     */
    void setSessionRetained(String recordingSessionId, boolean retained);

    /**
     * Downloads one of the session's files by its id, whatever that file is.
     *
     * <p>The id is all the hub needs: it resolves the file itself, so no caller has to hold the
     * listing entry and nothing walks the session directory to find one.
     */
    StreamedFile streamFile(String sessionId, String fileId);
}
