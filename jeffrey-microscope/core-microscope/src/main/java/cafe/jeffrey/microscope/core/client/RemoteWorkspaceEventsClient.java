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
import cafe.jeffrey.microscope.core.resources.response.WorkspaceEventResponse;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.List;

public class RemoteWorkspaceEventsClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceEventsClient.class);

    private final WorkspaceEventsServiceGrpc.WorkspaceEventsServiceBlockingStub stub;

    public RemoteWorkspaceEventsClient(GrpcServerConnection connection) {
        this.stub = WorkspaceEventsServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public WorkspaceEventsResult getEvents(String workspaceId, int limit) {
        GetWorkspaceEventsRequest.Builder builder = GetWorkspaceEventsRequest.newBuilder()
                .setWorkspaceId(workspaceId);
        if (limit > 0) {
            builder.setLimit(limit);
        }

        GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(builder.build());

        LOG.debug("Fetched workspace events via gRPC: workspaceId={} count={} total={} limit={}",
                workspaceId, response.getEventsCount(), response.getTotalCount(), limit);

        List<WorkspaceEventResponse> events = response.getEventsList().stream()
                .map(RemoteWorkspaceEventsClient::toResponse)
                .toList();
        return new WorkspaceEventsResult(events, response.getTotalCount());
    }

    public record WorkspaceEventsResult(List<WorkspaceEventResponse> events, long totalCount) {
    }

    private static WorkspaceEventResponse toResponse(WorkspaceEventInfo proto) {
        return new WorkspaceEventResponse(
                proto.getEventId(),
                proto.getOriginEventId().isEmpty() ? null : proto.getOriginEventId(),
                proto.getProjectId().isEmpty() ? null : proto.getProjectId(),
                proto.getWorkspaceRefId().isEmpty() ? null : proto.getWorkspaceRefId(),
                proto.getEventType().isEmpty() ? null : WorkspaceEventType.valueOf(proto.getEventType()),
                proto.getContent().isEmpty() ? null : proto.getContent(),
                proto.getOriginCreatedAt(),
                proto.getCreatedAt(),
                proto.getCreatedBy().isEmpty() ? null : proto.getCreatedBy());
    }
}
