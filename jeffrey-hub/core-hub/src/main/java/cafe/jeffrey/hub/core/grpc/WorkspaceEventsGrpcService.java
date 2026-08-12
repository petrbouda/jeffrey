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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.GetWorkspaceEventsRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceEventsResponse;
import cafe.jeffrey.hub.api.v1.WorkspaceEventInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceEventsServiceGrpc;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.WorkspaceEventQuery;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.List;
import java.util.Set;

public class WorkspaceEventsGrpcService extends WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsGrpcService.class);
    private static final int DEFAULT_LIMIT = 100;

    private final WorkspaceEventLogRepository workspaceEventLog;

    public WorkspaceEventsGrpcService(WorkspaceEventLogRepository workspaceEventLog) {
        this.workspaceEventLog = workspaceEventLog;
    }

    @Override
    public void getWorkspaceEvents(
            GetWorkspaceEventsRequest request,
            StreamObserver<GetWorkspaceEventsResponse> responseObserver) {

        GrpcUnary.respond(responseObserver, () -> {
            String workspaceId = request.getWorkspaceId();
            int limit = (request.hasLimit() && request.getLimit() > 0) ? request.getLimit() : DEFAULT_LIMIT;

            WorkspaceEventType typeFilter = request.hasEventType()
                    ? parseEventType(request.getEventType())
                    : null;
            Set<String> projectIds = Set.copyOf(request.getProjectIdsList());

            long totalCount = workspaceEventLog.count(workspaceId);
            List<WorkspaceEventInfo> events = workspaceEventLog
                    .findLatest(new WorkspaceEventQuery(workspaceId, typeFilter, projectIds, limit))
                    .stream()
                    .map(WorkspaceEventsGrpcService::toProto)
                    .toList();

            LOG.debug("Fetched workspace events via gRPC: workspaceId={} count={} total={} limit={}",
                    workspaceId, events.size(), totalCount, limit);

            return GetWorkspaceEventsResponse.newBuilder()
                    .addAllEvents(events)
                    .setTotalCount(totalCount)
                    .build();
        });
    }

    private static WorkspaceEventType parseEventType(String raw) {
        try {
            return WorkspaceEventType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw GrpcExceptions.invalidArgument("Invalid event type: " + raw);
        }
    }

    private static WorkspaceEventInfo toProto(WorkspaceEvent event) {
        return WorkspaceEventInfo.newBuilder()
                .setEventId(event.eventId() != null ? event.eventId() : 0)
                .setOriginEventId(ProtoMappers.orEmpty(event.originEventId()))
                .setProjectId(ProtoMappers.orEmpty(event.projectId()))
                .setWorkspaceRefId(ProtoMappers.orEmpty(event.workspaceRefId()))
                .setEventType(event.eventType() != null ? event.eventType().name() : "")
                .setContent(ProtoMappers.orEmpty(event.content()))
                .setOriginCreatedAt(event.originCreatedAt() != null ? event.originCreatedAt().toEpochMilli() : 0)
                .setCreatedAt(event.createdAt() != null ? event.createdAt().toEpochMilli() : 0)
                .setCreatedBy(ProtoMappers.orEmpty(event.createdBy()))
                .build();
    }
}
