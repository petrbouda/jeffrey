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
import pbouda.jeffrey.local.core.resources.response.ImportantMessageResponse;

import java.util.List;

public class RemoteMessagesClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteMessagesClient.class);

    private final MessagesServiceGrpc.MessagesServiceBlockingStub stub;

    public RemoteMessagesClient(GrpcServerConnection connection) {
        this.stub = MessagesServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<ImportantMessageResponse> getMessages(String workspaceId, String projectId, Long start, Long end) {
        GetMessagesRequest.Builder requestBuilder = GetMessagesRequest.newBuilder()
                .setWorkspaceId(workspaceId)
                .setProjectId(projectId);

        if (start != null) {
            requestBuilder.setStartAt(start);
        }
        if (end != null) {
            requestBuilder.setEndAt(end);
        }

        GetMessagesResponse response = stub.getMessages(requestBuilder.build());

        LOG.debug("Fetched messages via gRPC: workspaceId={} projectId={} count={}",
                workspaceId, projectId, response.getMessagesCount());

        return response.getMessagesList().stream()
                .map(RemoteMessagesClient::toResponse)
                .toList();
    }

    public List<ImportantMessageResponse> getAlerts(String workspaceId, String projectId, Long start, Long end) {
        GetAlertsRequest.Builder requestBuilder = GetAlertsRequest.newBuilder()
                .setWorkspaceId(workspaceId)
                .setProjectId(projectId);

        if (start != null) {
            requestBuilder.setStartAt(start);
        }
        if (end != null) {
            requestBuilder.setEndAt(end);
        }

        GetAlertsResponse response = stub.getAlerts(requestBuilder.build());

        LOG.debug("Fetched alerts via gRPC: workspaceId={} projectId={} count={}",
                workspaceId, projectId, response.getMessagesCount());

        return response.getMessagesList().stream()
                .map(RemoteMessagesClient::toResponse)
                .toList();
    }

    private static ImportantMessageResponse toResponse(ImportantMessage proto) {
        return new ImportantMessageResponse(
                proto.getType().isEmpty() ? null : proto.getType(),
                proto.getTitle().isEmpty() ? null : proto.getTitle(),
                proto.getMessage().isEmpty() ? null : proto.getMessage(),
                fromProtoSeverity(proto.getSeverity()),
                proto.getCategory().isEmpty() ? null : proto.getCategory(),
                proto.getSource().isEmpty() ? null : proto.getSource(),
                proto.getIsAlert(),
                proto.getCreatedAt());
    }

    private static String fromProtoSeverity(pbouda.jeffrey.api.v1.Severity severity) {
        return switch (severity) {
            case SEVERITY_CRITICAL -> "CRITICAL";
            case SEVERITY_HIGH -> "HIGH";
            case SEVERITY_MEDIUM -> "MEDIUM";
            case SEVERITY_LOW -> "LOW";
            default -> "UNKNOWN";
        };
    }
}
