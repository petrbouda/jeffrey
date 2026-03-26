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
import pbouda.jeffrey.server.core.workspace.WorkspaceEventReader;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.List;
import java.util.stream.Stream;

public class WorkspaceEventsGrpcService extends WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsGrpcService.class);

    private final WorkspaceEventReader workspaceEventReader;

    public WorkspaceEventsGrpcService(WorkspaceEventReader workspaceEventReader) {
        this.workspaceEventReader = workspaceEventReader;
    }

    @Override
    public void getWorkspaceEvents(
            GetWorkspaceEventsRequest request,
            StreamObserver<GetWorkspaceEventsResponse> responseObserver) {

        try {
            Stream<WorkspaceEvent> eventStream = workspaceEventReader.findAll(request.getWorkspaceId()).stream();

            if (request.hasEventType()) {
                WorkspaceEventType filterType = WorkspaceEventType.valueOf(request.getEventType());
                eventStream = eventStream.filter(event -> event.eventType() == filterType);
            }

            List<WorkspaceEventInfo> events = eventStream
                    .map(WorkspaceEventsGrpcService::toProto)
                    .toList();

            LOG.debug("Fetched workspace events via gRPC: workspaceId={} count={}", request.getWorkspaceId(), events.size());

            GetWorkspaceEventsResponse response = GetWorkspaceEventsResponse.newBuilder()
                    .addAllEvents(events)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid event type: " + request.getEventType())
                    .asRuntimeException());
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get workspace events: workspaceId={}", request.getWorkspaceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private static WorkspaceEventInfo toProto(WorkspaceEvent event) {
        return WorkspaceEventInfo.newBuilder()
                .setEventId(event.eventId() != null ? event.eventId() : 0)
                .setOriginEventId(event.originEventId() != null ? event.originEventId() : "")
                .setProjectId(event.projectId() != null ? event.projectId() : "")
                .setWorkspaceId(event.workspaceId() != null ? event.workspaceId() : "")
                .setEventType(event.eventType() != null ? event.eventType().name() : "")
                .setContent(event.content() != null ? event.content() : "")
                .setOriginCreatedAt(event.originCreatedAt() != null ? event.originCreatedAt().toEpochMilli() : 0)
                .setCreatedAt(event.createdAt() != null ? event.createdAt().toEpochMilli() : 0)
                .setCreatedBy(event.createdBy() != null ? event.createdBy() : "")
                .build();
    }
}
