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

package cafe.jeffrey.microscope.connector.api.manager;

import cafe.jeffrey.microscope.common.model.repository.RecordingSession;
import cafe.jeffrey.microscope.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.common.model.repository.StreamedFile;

import java.util.List;

public interface RepositoryManager {

    /**
     * Lists the recording sessions that satisfy the filter, newest first. The filter is
     * evaluated by the connector, so the response carries only the sessions asked for. Each session
     * carries its {@code SessionSize} but not its files: those come from
     * {@link #recordingSession(String)}.
     */
    List<RecordingSession> listRecordingSessions(RecordingSessionFilter filter);

    /**
     * The session as the volume currently holds it, files included.
     *
     * @param sessionId the session to fetch
     * @return the session
     * @throws RuntimeException when the volume no longer holds it, or cannot be reached
     */
    RecordingSession recordingSession(String sessionId);

    /**
     * What the project occupies: the figure it arrived with, never measured on request.
     */
    RepositoryStatistics repositoryStatistics();

    void deleteRecordingSession(String recordingSessionId);

    /**
     * Downloads one of the session's files by its id, whatever that file is.
     *
     * <p>The id is all the connector needs: it resolves the file itself, so no caller has to hold the
     * listing entry and nothing walks the session directory to find one.
     */
    StreamedFile streamFile(String sessionId, String fileId);
}
