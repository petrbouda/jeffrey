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

package pbouda.jeffrey.local.core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.api.v1.*;
import pbouda.jeffrey.local.core.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.local.core.resources.response.RepositoryFileResponse;
import pbouda.jeffrey.local.core.resources.response.RepositoryStatisticsResponse;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.util.List;

public class RemoteRepositoryClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRepositoryClient.class);

    private final RepositoryServiceGrpc.RepositoryServiceBlockingStub stub;

    public RemoteRepositoryClient(GrpcServerConnection connection) {
        this.stub = RepositoryServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<RecordingSessionResponse> recordingSessions(String workspaceId, String projectId) {
        ListSessionsResponse response = stub.listSessions(
                ListSessionsRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .build());

        return response.getSessionsList().stream()
                .map(RemoteRepositoryClient::toSessionResponse)
                .toList();
    }

    public RecordingSessionResponse recordingSession(String workspaceId, String projectId, String sessionId) {
        GetSessionResponse response = stub.getSession(
                GetSessionRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .setSessionId(sessionId)
                        .build());

        return toSessionResponse(response.getSession());
    }

    public RepositoryStatisticsResponse repositoryStatistics(String workspaceId, String projectId) {
        GetRepositoryStatisticsResponse response = stub.getRepositoryStatistics(
                GetRepositoryStatisticsRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .build());

        return new RepositoryStatisticsResponse(
                response.getTotalSessions(),
                parseRecordingStatus(response.getSessionStatus()),
                response.getLastActivityTime(),
                response.getTotalSize(),
                response.getTotalFiles(),
                response.getBiggestSessionSize(),
                response.getJfrFiles(),
                response.getJfrSize(),
                response.getHeapDumpFiles(),
                response.getHeapDumpSize(),
                response.getLogFiles(),
                response.getLogSize(),
                response.getAppLogFiles(),
                response.getAppLogSize(),
                response.getErrorLogFiles(),
                response.getErrorLogSize(),
                response.getOtherFiles(),
                response.getOtherSize());
    }

    public void deleteSession(String workspaceId, String projectId, String sessionId) {
        stub.deleteSession(
                DeleteSessionRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .setSessionId(sessionId)
                        .build());

        LOG.debug("Deleted session via gRPC: workspaceId={} projectId={} sessionId={}",
                workspaceId, projectId, sessionId);
    }

    public void deleteFilesInSession(String workspaceId, String projectId, String sessionId, List<String> fileIds) {
        stub.deleteFilesInSession(
                DeleteFilesInSessionRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .setSessionId(sessionId)
                        .addAllFileIds(fileIds)
                        .build());

        LOG.debug("Deleted files in session via gRPC: workspaceId={} projectId={} sessionId={} fileCount={}",
                workspaceId, projectId, sessionId, fileIds.size());
    }

    private static RecordingSessionResponse toSessionResponse(RecordingSession proto) {
        List<RepositoryFileResponse> files = proto.getFilesList().stream()
                .map(RemoteRepositoryClient::toFileResponse)
                .toList();

        return new RecordingSessionResponse(
                proto.getId(),
                proto.getName().isEmpty() ? null : proto.getName(),
                proto.hasInstanceId() ? proto.getInstanceId() : null,
                parseLongOrNull(proto.getCreatedAt()),
                proto.hasFinishedAt() ? parseLongOrNull(proto.getFinishedAt()) : null,
                parseRecordingStatus(proto.getStatus()),
                proto.hasProfilerSettings() ? proto.getProfilerSettings() : null,
                proto.hasDuration() ? parseLongOrNull(proto.getDuration()) : null,
                files);
    }

    private static RepositoryFileResponse toFileResponse(RepositoryFile proto) {
        return new RepositoryFileResponse(
                proto.getId(),
                proto.getName(),
                parseLongOrNull(proto.getCreatedAt()),
                proto.getSize(),
                parseFileType(proto.getFileType()),
                parseRecordingStatus(proto.getStatus()));
    }

    private static RecordingStatus parseRecordingStatus(String status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        try {
            return RecordingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SupportedRecordingFile parseFileType(String fileType) {
        if (fileType == null || fileType.isEmpty()) {
            return null;
        }
        try {
            return SupportedRecordingFile.valueOf(fileType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
