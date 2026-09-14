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

import cafe.jeffrey.recordings.core.download.ProgressCallback;

import java.util.List;

public interface RecordingsDownloadManager {

    /**
     * Downloads every finished file of the session and stores them as one local recording: the
     * JFR chunks assembled into the recording, everything else beside it.
     *
     * @param sessionId the upstream session to download
     * @return id of the recording created in the local store, so the caller can go on to
     * analyse it without having to search the store for whatever appeared last
     */
    String downloadSession(String sessionId);

    /**
     * Downloads the named files of the session and stores them as one local recording.
     *
     * @param sessionId the upstream session to download
     * @param fileIds   ids of the files to take from that session; at least one must be a chunk
     * @return id of the recording created in the local store
     */
    String downloadFiles(String sessionId, List<String> fileIds);

    /**
     * As {@link #downloadFiles(String, List)}, reporting each file's progress to the callback,
     * which may also cancel the download.
     */
    String downloadFiles(String sessionId, List<String> fileIds, ProgressCallback progress);
}
