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

package cafe.jeffrey.microscope.grpc.client;

import cafe.jeffrey.hub.api.v1.ActivityScope;
import cafe.jeffrey.hub.api.v1.CancelActivityRequest;
import cafe.jeffrey.hub.api.v1.EventActivitySnapshot;
import cafe.jeffrey.hub.api.v1.GetActivityRequest;
import cafe.jeffrey.hub.api.v1.StartActivityRequest;
import cafe.jeffrey.shared.common.activity.ActivityOrder;
import cafe.jeffrey.shared.common.activity.ActivityState;

import java.util.List;
import java.util.Map;

/**
 * The single place protobuf meets Microscope's activity records. Everything above
 * {@link EventStreamingClient} works with the records alone, so a change to the Hub API stops here.
 */
final class ActivityCodec {

    private static final Map<ActivityOrder, cafe.jeffrey.hub.api.v1.ActivityOrder> ORDER_TO_WIRE = Map.of(
            ActivityOrder.EVENTS, cafe.jeffrey.hub.api.v1.ActivityOrder.ACTIVITY_ORDER_EVENTS,
            ActivityOrder.TYPES, cafe.jeffrey.hub.api.v1.ActivityOrder.ACTIVITY_ORDER_TYPES,
            ActivityOrder.TIME, cafe.jeffrey.hub.api.v1.ActivityOrder.ACTIVITY_ORDER_TIME);

    private static final Map<cafe.jeffrey.hub.api.v1.ActivityOrder, ActivityOrder> ORDER_FROM_WIRE = Map.of(
            cafe.jeffrey.hub.api.v1.ActivityOrder.ACTIVITY_ORDER_UNSPECIFIED, ActivityOrder.EVENTS,
            cafe.jeffrey.hub.api.v1.ActivityOrder.ACTIVITY_ORDER_EVENTS, ActivityOrder.EVENTS,
            cafe.jeffrey.hub.api.v1.ActivityOrder.ACTIVITY_ORDER_TYPES, ActivityOrder.TYPES,
            cafe.jeffrey.hub.api.v1.ActivityOrder.ACTIVITY_ORDER_TIME, ActivityOrder.TIME);

    private static final Map<cafe.jeffrey.hub.api.v1.ActivityState, ActivityState> STATE_FROM_WIRE = Map.of(
            cafe.jeffrey.hub.api.v1.ActivityState.ACTIVITY_STATE_QUEUED, ActivityState.QUEUED,
            cafe.jeffrey.hub.api.v1.ActivityState.ACTIVITY_STATE_RUNNING, ActivityState.RUNNING,
            cafe.jeffrey.hub.api.v1.ActivityState.ACTIVITY_STATE_CANCEL_REQUESTED, ActivityState.CANCEL_REQUESTED,
            cafe.jeffrey.hub.api.v1.ActivityState.ACTIVITY_STATE_COMPLETED, ActivityState.COMPLETED,
            cafe.jeffrey.hub.api.v1.ActivityState.ACTIVITY_STATE_CANCELLED, ActivityState.CANCELLED,
            cafe.jeffrey.hub.api.v1.ActivityState.ACTIVITY_STATE_FAILED, ActivityState.FAILED);

    private ActivityCodec() {
    }

    static StartActivityRequest start(ActivityScanRequest request) {
        return StartActivityRequest.newBuilder()
                .setScope(ActivityScope.newBuilder()
                        .setWorkspaceId(request.workspaceId())
                        .setProjectId(request.projectId())
                        .setSessionId(request.sessionId()))
                .setStartTime(request.startTime())
                .setEndTime(request.endTime())
                .setBucketSeconds(request.bucketSeconds())
                .addAllEventTypes(request.eventTypes())
                .build();
    }

    static GetActivityRequest get(ActivityScanQuery query) {
        return GetActivityRequest.newBuilder()
                .setScope(scope(query.target()))
                .setScanId(query.target().scanId())
                .setOrder(ORDER_TO_WIRE.get(query.order()))
                .setLimit(query.limit())
                .setOffset(query.offset())
                .build();
    }

    static CancelActivityRequest cancel(ActivityScanTarget target) {
        return CancelActivityRequest.newBuilder()
                .setScope(scope(target))
                .setScanId(target.scanId())
                .build();
    }

    private static ActivityScope scope(ActivityScanTarget target) {
        return ActivityScope.newBuilder()
                .setWorkspaceId(target.workspaceId())
                .setProjectId(target.projectId())
                .setSessionId(target.sessionId())
                .build();
    }

    static ActivityScanSnapshot snapshot(EventActivitySnapshot wire) {
        ActivityState state = STATE_FROM_WIRE.get(wire.getState());
        if (state == null) {
            throw new IllegalStateException("Hub reported an unknown activity state: " + wire.getState());
        }
        ActivityOrder order = ORDER_FROM_WIRE.get(wire.getOrder());
        if (order == null) {
            throw new IllegalStateException("Hub reported an unknown activity order: " + wire.getOrder());
        }
        return new ActivityScanSnapshot(
                wire.getScanId(),
                wire.getScope().getWorkspaceId(),
                wire.getScope().getProjectId(),
                wire.getScope().getSessionId(),
                state,
                wire.getStartedAt(),
                wire.hasFinishedAt() ? wire.getFinishedAt() : null,
                wire.getComplete(),
                wire.getCoverageKnown(),
                wire.getSourceErrors(),
                wire.getFilesTotal(),
                wire.hasError() ? wire.getError() : null,
                wire.getStartTime(),
                wire.getEndTime(),
                wire.getBucketMillis(),
                List.copyOf(wire.getRequestedEventTypesList()),
                wire.getTotalEvents(),
                wire.getDistinctEventTypes(),
                wire.getTotalBuckets(),
                order,
                wire.getOffset(),
                wire.getOmittedBuckets(),
                wire.getHasMoreBuckets(),
                wire.getBucketsList().stream()
                        .map(bucket -> new ActivityScanSnapshot.Bucket(
                                bucket.getStartTime(),
                                bucket.getEndTime(),
                                bucket.getEventCount(),
                                bucket.getDistinctEventTypes(),
                                bucket.getOmittedTypes(),
                                bucket.getEventTypesList().stream()
                                        .map(type -> new ActivityScanSnapshot.TypeCount(
                                                type.getEventType(), type.getCount()))
                                        .toList()))
                        .toList());
    }
}
