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
import org.springframework.http.ResponseEntity;
import pbouda.jeffrey.platform.resources.pub.PublicApiPaths;
import pbouda.jeffrey.platform.resources.request.FileDownloadRequest;
import pbouda.jeffrey.platform.resources.request.FilesDownloadRequest;
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.exception.ErrorResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RemoteRecordingStreamClientImpl implements RemoteRecordingStreamClient {

    private final RemoteHttpInvoker invoker;

    public RemoteRecordingStreamClientImpl(RemoteHttpInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public CompletableFuture<Resource> downloadRecordings(
            String workspaceId, String projectId, String sessionId, List<String> recordingIds) {
        return CompletableFuture.supplyAsync(() -> {
            return invoker.post(() -> {
                return invoker.restClient().post()
                        .uri(PublicApiPaths.SESSION_RECORDINGS, workspaceId, projectId, sessionId)
                        .body(new FilesDownloadRequest(recordingIds))
                        .retrieve()
                        .toEntity(Resource.class);
            }).getBody();
        }, Schedulers.sharedVirtual());
    }

    @Override
    public CompletableFuture<Resource> downloadFile(
            String workspaceId, String projectId, String sessionId, String fileId) {
        return CompletableFuture.supplyAsync(() -> {
            return invoker.post(() -> {
                return invoker.restClient().post()
                        .uri(PublicApiPaths.SESSION_ARTIFACT, workspaceId, projectId, sessionId)
                        .body(new FileDownloadRequest(fileId))
                        .retrieve()
                        .toEntity(Resource.class);
            }).getBody();
        }, Schedulers.sharedVirtual());
    }

    @Override
    public void streamRecordings(
            String workspaceId, String projectId, String sessionId,
            List<String> recordingIds, InputStreamConsumer consumer) {

        invoker.streaming(() -> {
            return invoker.restClient().post()
                    .uri(PublicApiPaths.SESSION_RECORDINGS, workspaceId, projectId, sessionId)
                    .body(new FilesDownloadRequest(recordingIds))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw invoker.toRemoteError(response.getStatusCode().value(),
                                    () -> response.bodyTo(ErrorResponse.class));
                        }
                        consumer.accept(response.getBody(), response.getHeaders().getContentLength());
                        return response.getStatusCode().value();
                    });
        });
    }

    @Override
    public void streamFile(
            String workspaceId, String projectId, String sessionId,
            String fileId, InputStreamConsumer consumer) {

        invoker.streaming(() -> {
            return invoker.restClient().post()
                    .uri(PublicApiPaths.SESSION_ARTIFACT, workspaceId, projectId, sessionId)
                    .body(new FileDownloadRequest(fileId))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw invoker.toRemoteError(response.getStatusCode().value(),
                                    () -> response.bodyTo(ErrorResponse.class));
                        }
                        consumer.accept(response.getBody(), response.getHeaders().getContentLength());
                        return response.getStatusCode().value();
                    });
        });
    }

    @Override
    public void streamSingleFile(
            String workspaceId, String projectId, String sessionId,
            String fileId, InputStreamConsumer consumer) {

        invoker.streaming(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.SESSION_FILE_DOWNLOAD, workspaceId, projectId, sessionId, fileId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw invoker.toRemoteError(response.getStatusCode().value(),
                                    () -> response.bodyTo(ErrorResponse.class));
                        }
                        consumer.accept(response.getBody(), response.getHeaders().getContentLength());
                        return response.getStatusCode().value();
                    });
        });
    }
}
