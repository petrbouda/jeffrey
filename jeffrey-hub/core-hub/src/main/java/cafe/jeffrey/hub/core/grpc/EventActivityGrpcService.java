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

import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.activity.ActivityRequest;
import cafe.jeffrey.hub.core.activity.ActivityScanRef;
import cafe.jeffrey.hub.core.activity.ActivitySnapshot;
import cafe.jeffrey.hub.core.activity.HubActivityService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.Locale;
import java.util.Set;

/** The Hub owns scanning and counters. Microscope owns their MCP presentation. */
public final class EventActivityGrpcService extends EventActivityServiceGrpc.EventActivityServiceImplBase {

    private static final long DEFAULT_BUCKET_SECONDS = 300;
    private static final long MILLIS_PER_SECOND = 1000;
    private static final int DEFAULT_LIMIT = 20;
    private static final String STATE_PREFIX = "ACTIVITY_STATE_";
    private static final String ORDER_PREFIX = "ACTIVITY_ORDER_";

    private final HubActivityService service;

    public EventActivityGrpcService(HubActivityService service) {
        this.service = service;
    }

    @Override
    public void startActivity(StartActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
        GrpcUnary.respond(observer, () -> {
            if (!request.hasStartTime() || !request.hasEndTime()) {
                throw new IllegalArgumentException("start_time and end_time are required");
            }
            long width;
            try {
                width = Math.multiplyExact(
                        request.hasBucketSeconds() ? request.getBucketSeconds() : DEFAULT_BUCKET_SECONDS,
                        MILLIS_PER_SECOND);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("bucket_seconds is too large", e);
            }
            var scope = request.getScope();
            var activity = new ActivityRequest(
                    scope.getWorkspaceId(),
                    scope.getProjectId(),
                    scope.getSessionId(),
                    request.getStartTime(),
                    request.getEndTime(),
                    width,
                    Set.copyOf(request.getEventTypesList()));

            String id;
            try {
                id = service.start(activity);
            } catch (IllegalStateException e) {
                throw Status.RESOURCE_EXHAUSTED
                        .withDescription(e.getMessage())
                        .asRuntimeException();
            }
            return response(service.status(ref(scope, id), "events", DEFAULT_LIMIT));
        });
    }

    @Override
    public void getActivity(GetActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
        GrpcUnary.respond(observer, () -> response(service.status(
                ref(request.getScope(), request.getScanId()),
                order(request.getOrder()),
                request.hasLimit() ? request.getLimit() : DEFAULT_LIMIT)));
    }

    @Override
    public void cancelActivity(CancelActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
        GrpcUnary.respond(observer, () -> response(service.cancel(ref(request.getScope(), request.getScanId()))));
    }

    private static ActivityScanRef ref(ActivityScope scope, String id) {
        return new ActivityScanRef(scope.getWorkspaceId(), scope.getProjectId(), scope.getSessionId(), id);
    }

    private static String order(ActivityOrder order) {
        return switch (order) {
            case ACTIVITY_ORDER_UNSPECIFIED, ACTIVITY_ORDER_EVENTS -> "events";
            case ACTIVITY_ORDER_TYPES -> "types";
            case ACTIVITY_ORDER_TIME -> "time";
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unknown activity order");
        };
    }

    private static EventActivitySnapshot response(ActivitySnapshot snapshot) {
        var request = snapshot.request();
        var summary = snapshot.summary();

        var result = EventActivitySnapshot.newBuilder()
                .setScanId(snapshot.scanId())
                .setScope(ActivityScope.newBuilder()
                        .setWorkspaceId(request.workspaceId())
                        .setProjectId(request.projectId())
                        .setSessionId(request.sessionId()))
                .setState(ActivityState.valueOf(STATE_PREFIX + snapshot.status().toUpperCase(Locale.ROOT)))
                .setStartedAt(snapshot.startedAt().toEpochMilli())
                .setComplete(snapshot.complete())
                .setCoverageKnown(snapshot.coverageKnown())
                .setSourceErrors(snapshot.sourceErrors())
                .setFilesTotal(snapshot.filesTotal())
                .setStartTime(request.startTime())
                .setEndTime(request.endTime())
                .setBucketMillis(request.bucketMillis())
                .addAllEventTypes(request.eventTypes().stream().sorted().toList())
                .setTotalEvents(summary.totalEvents())
                .setDistinctEventTypes(summary.distinctEventTypes())
                .setTotalBuckets(summary.totalBuckets())
                .setOrder(ActivityOrder.valueOf(ORDER_PREFIX + summary.order().toUpperCase(Locale.ROOT)))
                .setOmittedBuckets(summary.omittedBuckets());

        if (snapshot.finishedAt() != null) {
            result.setFinishedAt(snapshot.finishedAt().toEpochMilli());
        }
        if (snapshot.error() != null) {
            result.setError(snapshot.error());
        }

        for (var bucket : summary.buckets()) {
            var row = ActivityBucket.newBuilder()
                    .setStartTime(bucket.startTime())
                    .setEndTime(bucket.endTime())
                    .setEventCount(bucket.eventCount())
                    .setDistinctEventTypes(bucket.distinctEventTypes())
                    .setOmittedTypes(bucket.omittedTypes());

            for (var type : bucket.eventTypes()) {
                row.addEventTypes(ActivityTypeCount.newBuilder()
                        .setEventType(type.eventType())
                        .setCount(type.count()));
            }
            result.addBuckets(row);
        }

        return result.build();
    }
}
