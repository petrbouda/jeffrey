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

package cafe.jeffrey.hub.client.manager;

import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;

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

    StreamedRecordingFile streamFile(String sessionId, String fileId);
}
