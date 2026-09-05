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

package cafe.jeffrey.recordings.core;

import java.util.List;

public interface RecordingsDownloadManager {

    /**
     * Downloads every finished file of the session and stores it as one local recording.
     *
     * @param recordingSessionId the upstream session to download
     * @return id of the recording created in the local store, so the caller can go on to
     * analyse it without having to search the store for whatever appeared last
     */
    String mergeAndDownloadSession(String recordingSessionId);

    /**
     * Downloads the named files of the session and stores them as one local recording.
     *
     * @param recordingSessionId the upstream session to download
     * @param rawRecordingIds    ids of the files to take from that session
     * @return id of the recording created in the local store
     */
    String mergeAndDownloadRecordings(String recordingSessionId, List<String> rawRecordingIds);
}
