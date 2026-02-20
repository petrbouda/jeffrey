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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RemoteRecordingStreamClient {

    /**
     * Consumer for streaming HTTP response body.
     *
     * @param inputStream   the raw HTTP response body stream
     * @param contentLength the Content-Length from the HTTP response, or -1 if unknown
     */
    @FunctionalInterface
    interface InputStreamConsumer {
        void accept(InputStream inputStream, long contentLength) throws IOException;
    }

    CompletableFuture<Resource> downloadRecordings(
            String workspaceId, String projectId, String sessionId, List<String> recordingIds);

    CompletableFuture<Resource> downloadFile(
            String workspaceId, String projectId, String sessionId, String fileId);

    void streamRecordings(
            String workspaceId, String projectId, String sessionId,
            List<String> recordingIds, InputStreamConsumer consumer);

    void streamFile(
            String workspaceId, String projectId, String sessionId,
            String fileId, InputStreamConsumer consumer);
}
