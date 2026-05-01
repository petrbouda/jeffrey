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

package cafe.jeffrey.microscope.core.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.microscope.core.resources.response.RecordingSessionResponse;
import cafe.jeffrey.microscope.core.resources.response.RepositoryFileResponse;
import cafe.jeffrey.microscope.core.resources.response.RepositoryStatisticsResponse;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.util.List;

public class RemoteRepositoryClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRepositoryClient.class);

    private final RepositoryServiceGrpc.RepositoryServiceBlockingStub stub;

    public RemoteRepositoryClient(GrpcServerConnection connection) {
        this.stub = RepositoryServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<RecordingSessionResponse> recordingSessions(String projectId) {
        ListSessionsResponse response = stub.listSessions(
                ListSessionsRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());

        return response.getSessionsList().stream()
                .map(RemoteRepositoryClient::toSessionResponse)
                .toList();
    }

    public RecordingSessionResponse recordingSession(String sessionId) {
        GetSessionResponse response = stub.getSession(
                GetSessionRequest.newBuilder()
                        .setSessionId(sessionId)
                        .build());

        return toSessionResponse(response.getSession());
    }

    public RepositoryStatisticsResponse repositoryStatistics(String projectId) {
        GetRepositoryStatisticsResponse response = stub.getRepositoryStatistics(
                GetRepositoryStatisticsRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());

        return new RepositoryStatisticsResponse(
                response.getTotalSessions(),
                fromProtoRecordingStatus(response.getSessionStatus()),
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

    public void deleteSession(String sessionId) {
        stub.deleteSession(
                DeleteSessionRequest.newBuilder()
                        .setSessionId(sessionId)
                        .build());

        LOG.debug("Deleted session via gRPC: sessionId={}", sessionId);
    }

    public void deleteFilesInSession(String sessionId, List<String> fileIds) {
        stub.deleteFilesInSession(
                DeleteFilesInSessionRequest.newBuilder()
                        .setSessionId(sessionId)
                        .addAllFileIds(fileIds)
                        .build());

        LOG.debug("Deleted files in session via gRPC: sessionId={} fileCount={}",
                sessionId, fileIds.size());
    }

    private static RecordingSessionResponse toSessionResponse(RecordingSession proto) {
        List<RepositoryFileResponse> files = proto.getFilesList().stream()
                .map(RemoteRepositoryClient::toFileResponse)
                .toList();

        return new RecordingSessionResponse(
                proto.getId(),
                proto.getName().isEmpty() ? null : proto.getName(),
                proto.hasInstanceId() ? proto.getInstanceId() : null,
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                fromProtoRecordingStatus(proto.getStatus()),
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null,
                files);
    }

    private static RepositoryFileResponse toFileResponse(RepositoryFile proto) {
        return new RepositoryFileResponse(
                proto.getId(),
                proto.getName(),
                proto.getCreatedAt() != 0 ? proto.getCreatedAt() : null,
                proto.getSize(),
                parseFileType(proto.getFileType()),
                fromProtoRecordingStatus(proto.getStatus()),
                proto.getIsRecording());
    }

    private static RecordingStatus fromProtoRecordingStatus(cafe.jeffrey.server.api.v1.RecordingStatus status) {
        return switch (status) {
            case RECORDING_STATUS_ACTIVE -> RecordingStatus.ACTIVE;
            case RECORDING_STATUS_FINISHED -> RecordingStatus.FINISHED;
            default -> RecordingStatus.UNKNOWN;
        };
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
