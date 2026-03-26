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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.shared.common.model.time.AbsoluteTimeRange;

import java.util.List;

public class MessagesGrpcService extends MessagesServiceGrpc.MessagesServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(MessagesGrpcService.class);

    private final WorkspacesManager workspacesManager;

    public MessagesGrpcService(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @Override
    public void getMessages(GetMessagesRequest request, StreamObserver<GetMessagesResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            Long startMillis = request.hasStartAt() ? request.getStartAt() : null;
            Long endMillis = request.hasEndAt() ? request.getEndAt() : null;

            List<ImportantMessage> messages = project.messagesManager()
                    .getMessages(AbsoluteTimeRange.ofEpochMillis(startMillis, endMillis)).stream()
                    .map(MessagesGrpcService::toProto)
                    .toList();

            LOG.debug("Fetched messages via gRPC: projectId={} count={}", request.getProjectId(), messages.size());

            GetMessagesResponse response = GetMessagesResponse.newBuilder()
                    .addAllMessages(messages)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get messages: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getAlerts(GetAlertsRequest request, StreamObserver<GetAlertsResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            Long startMillis = request.hasStartAt() ? request.getStartAt() : null;
            Long endMillis = request.hasEndAt() ? request.getEndAt() : null;

            List<ImportantMessage> alerts = project.messagesManager()
                    .getAlerts(AbsoluteTimeRange.ofEpochMillis(startMillis, endMillis)).stream()
                    .map(MessagesGrpcService::toProto)
                    .toList();

            LOG.debug("Fetched alerts via gRPC: projectId={} count={}", request.getProjectId(), alerts.size());

            GetAlertsResponse response = GetAlertsResponse.newBuilder()
                    .addAllMessages(alerts)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get alerts: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private ProjectManager findProject(String workspaceId, String projectId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Workspace not found: " + workspaceId)
                        .asRuntimeException());
        return workspace.projectsManager().project(projectId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Project not found: " + projectId)
                        .asRuntimeException());
    }

    private static ImportantMessage toProto(pbouda.jeffrey.shared.common.model.ImportantMessage msg) {
        return ImportantMessage.newBuilder()
                .setType(msg.type() != null ? msg.type() : "")
                .setTitle(msg.title() != null ? msg.title() : "")
                .setMessage(msg.message() != null ? msg.message() : "")
                .setSeverity(toProtoSeverity(msg.severity()))
                .setCategory(msg.category() != null ? msg.category() : "")
                .setSource(msg.source() != null ? msg.source() : "")
                .setIsAlert(msg.isAlert())
                .setCreatedAt(msg.createdAtUs().toEpochMilli())
                .build();
    }

    private static pbouda.jeffrey.server.api.v1.Severity toProtoSeverity(
            pbouda.jeffrey.shared.common.model.Severity severity) {
        return switch (severity) {
            case CRITICAL -> pbouda.jeffrey.server.api.v1.Severity.SEVERITY_CRITICAL;
            case HIGH -> pbouda.jeffrey.server.api.v1.Severity.SEVERITY_HIGH;
            case MEDIUM -> pbouda.jeffrey.server.api.v1.Severity.SEVERITY_MEDIUM;
            case LOW -> pbouda.jeffrey.server.api.v1.Severity.SEVERITY_LOW;
        };
    }
}
