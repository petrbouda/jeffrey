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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.GetWorkspaceEventsRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceEventsResponse;
import cafe.jeffrey.hub.api.v1.WorkspaceEventInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceEventsServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.stub.StreamObserver;

import java.util.List;

/** Stub {@code WorkspaceEventsService} returning the workspace's in-memory event log (latest first). */
public class StubWorkspaceEventsService extends WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase {

    private static final int DEFAULT_LIMIT = 100;

    private final StubDataset dataset;

    public StubWorkspaceEventsService(StubDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void getWorkspaceEvents(
            GetWorkspaceEventsRequest request,
            StreamObserver<GetWorkspaceEventsResponse> responseObserver) {

        List<StubDataset.Event> allEvents = dataset.workspace(request.getWorkspaceId())
                .map(StubDataset.Workspace::events)
                .orElse(List.of());

        List<StubDataset.Event> filtered = allEvents.stream()
                .filter(event -> !request.hasEventType() || event.eventType().equals(request.getEventType()))
                .toList();

        int limit = request.hasLimit() && request.getLimit() > 0 ? request.getLimit() : DEFAULT_LIMIT;

        GetWorkspaceEventsResponse.Builder builder = GetWorkspaceEventsResponse.newBuilder()
                .setTotalCount(allEvents.size());
        filtered.stream()
                .limit(limit)
                .forEach(event -> builder.addEvents(toProto(event)));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private static WorkspaceEventInfo toProto(StubDataset.Event event) {
        return WorkspaceEventInfo.newBuilder()
                .setEventId(event.eventId())
                .setOriginEventId(event.originEventId())
                .setProjectId(event.projectId())
                .setWorkspaceRefId(event.workspaceRefId())
                .setEventType(event.eventType())
                .setContent(event.content())
                .setOriginCreatedAt(event.originCreatedAt().toEpochMilli())
                .setCreatedAt(event.createdAt().toEpochMilli())
                .setCreatedBy(event.createdBy())
                .build();
    }
}
