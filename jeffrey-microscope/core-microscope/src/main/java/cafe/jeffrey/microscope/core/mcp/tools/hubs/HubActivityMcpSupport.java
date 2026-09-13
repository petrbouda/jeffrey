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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.microscope.core.manager.EventStreamingManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.shared.common.Json;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Converts Hub summaries to bounded MCP results using the existing strict Hub resolver. */
public final class HubActivityMcpSupport {
    private static final int MAX_REF_LENGTH = 2048;
    private static final int MAX_ID_LENGTH = 512;
    private static final int MAX_FILTER_LENGTH = 4096;
    private static final int MAX_FILTER_TYPES = 16;
    private static final int MAX_TYPE_LENGTH = 256;
    private static final int MAX_BUCKETS = 288;
    private static final int MAX_RESULT_BUCKETS = 20;
    private static final long DEFAULT_BUCKET_SECONDS = 300;
    private static final long MILLIS_PER_SECOND = 1000;
    private static final String STATE_PREFIX = "ACTIVITY_STATE_";
    private static final String ORDER_PREFIX = "ACTIVITY_ORDER_";
    private final ProjectManagerResolver resolver;
    private final Duration timeout;
    private final ScheduledExecutorService deadlines;

    public HubActivityMcpSupport(ProjectManagerResolver resolver, Duration timeout, ScheduledExecutorService deadlines) {
        this.resolver = resolver;
        this.timeout = timeout;
        this.deadlines = deadlines;
    }

    public McpToolResult start(String sessionRef, long startTime, long endTime, Long bucketSeconds, String eventTypes) {
        var ref = reference(sessionRef);
        long seconds = bucketSeconds == null ? DEFAULT_BUCKET_SECONDS : bucketSeconds;
        long duration;
        long width;
        try {
            duration = Math.subtractExact(endTime, startTime);
            width = Math.multiplyExact(seconds, MILLIS_PER_SECOND);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Time window or bucket width is too large", e);
        }
        if (duration <= 0 || width <= 0 || (duration - 1) / width >= MAX_BUCKETS) {
            throw new IllegalArgumentException("Use startTime < endTime and a positive bucket size producing at most 288 buckets");
        }
        if (eventTypes != null && eventTypes.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException("eventTypes is too long");
        }
        Set<String> types = eventTypes == null || eventTypes.isBlank() ? Set.of()
                : Arrays.stream(eventTypes.split(",", -1)).map(String::trim).collect(Collectors.toSet());
        if (types.size() > MAX_FILTER_TYPES || types.stream().anyMatch(type -> type.isBlank() || type.length() > MAX_TYPE_LENGTH)) {
            throw new IllegalArgumentException("Specify at most 16 nonempty event types, each at most 256 characters");
        }
        var request = StartActivityRequest.newBuilder().setScope(scope(ref)).setStartTime(startTime).setEndTime(endTime)
                .setBucketSeconds(seconds).addAllEventTypes(types).build();
        return call(ref, manager -> manager.startActivity(request));
    }

    public McpToolResult status(String sessionRef, String scanId, String order, Integer limit) {
        var ref = reference(sessionRef);
        validateId(scanId);
        int selected = limit == null ? MAX_RESULT_BUCKETS : limit;
        if (selected < 1 || selected > MAX_RESULT_BUCKETS) {
            throw new IllegalArgumentException("limit must be 1–20");
        }
        var sort = switch (order == null ? "events" : order) {
            case "events" -> ActivityOrder.ACTIVITY_ORDER_EVENTS;
            case "types" -> ActivityOrder.ACTIVITY_ORDER_TYPES;
            case "time" -> ActivityOrder.ACTIVITY_ORDER_TIME;
            default -> throw new IllegalArgumentException("order must be events, types or time");
        };
        var request = GetActivityRequest.newBuilder().setScope(scope(ref)).setScanId(scanId).setOrder(sort).setLimit(selected).build();
        return call(ref, manager -> manager.getActivity(request));
    }

    public McpToolResult cancel(String sessionRef, String scanId) {
        var ref = reference(sessionRef);
        validateId(scanId);
        var request = CancelActivityRequest.newBuilder().setScope(scope(ref)).setScanId(scanId).build();
        return call(ref, manager -> manager.cancelActivity(request));
    }

    private McpToolResult call(HubSessionRef ref, Function<EventStreamingManager, EventActivitySnapshot> operation) {
        var deadline = Deadline.after(timeout.toNanos(), TimeUnit.NANOSECONDS);
        Context.CancellableContext context = Context.current().withDeadline(deadline, deadlines);
        try {
            var snapshot = context.call(() -> operation.apply(resolver.resolveStrict(ref.hubId(), ref.workspaceId(), ref.projectId())
                    .projectManager().eventStreamingManager()));
            if (!snapshot.getScope().equals(scope(ref))) {
                throw new IllegalStateException("Hub returned activity for a different session");
            }
            return result(ref, snapshot);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String message = switch (Status.fromThrowable(e).getCode()) {
                case UNIMPLEMENTED -> "This Hub does not support event activity summaries; upgrade the Hub";
                case NOT_FOUND -> "Unknown or expired scan for this session on the Hub";
                case DEADLINE_EXCEEDED -> "Hub activity request timed out; a started scan may still be running";
                default -> "Hub activity request failed: " + e.getMessage();
            };
            throw new IllegalStateException(message, e);
        } finally {
            context.cancel(null);
        }
    }

    private static HubSessionRef reference(String sessionRef) {
        if (sessionRef == null || sessionRef.length() > MAX_REF_LENGTH) {
            throw new IllegalArgumentException("Pass a complete session_ref from hubs_sessions (at most 2048 characters)");
        }
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        validateId(ref.workspaceId());
        validateId(ref.projectId());
        validateId(ref.sessionId());
        return ref;
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank() || id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("A nonempty ID of at most 512 characters is required");
        }
    }

    private static ActivityScope scope(HubSessionRef ref) {
        return ActivityScope.newBuilder().setWorkspaceId(ref.workspaceId()).setProjectId(ref.projectId())
                .setSessionId(ref.sessionId()).build();
    }

    private static McpToolResult result(HubSessionRef ref, EventActivitySnapshot snapshot) {
        ObjectNode result = Json.createObject().put("sessionRef", ref.encode()).put("scanId", snapshot.getScanId())
                .put("status", snapshot.getState().name().replace(STATE_PREFIX, "").toLowerCase(Locale.ROOT))
                .put("startedAt", snapshot.getStartedAt()).put("complete", snapshot.getComplete())
                .put("coverageKnown", snapshot.getCoverageKnown()).put("sourceErrors", snapshot.getSourceErrors())
                .put("filesTotal", snapshot.getFilesTotal()).put("startTime", snapshot.getStartTime())
                .put("endTime", snapshot.getEndTime()).put("bucketMillis", snapshot.getBucketMillis())
                .put("totalEvents", snapshot.getTotalEvents()).put("distinctEventTypes", snapshot.getDistinctEventTypes())
                .put("totalBuckets", snapshot.getTotalBuckets()).put("omittedBuckets", snapshot.getOmittedBuckets())
                .put("order", snapshot.getOrder().name().replace(ORDER_PREFIX, "").toLowerCase(Locale.ROOT));
        if (snapshot.hasFinishedAt()) {
            result.put("finishedAt", snapshot.getFinishedAt());
        } else {
            result.putNull("finishedAt");
        }
        result.put("error", snapshot.hasError() ? snapshot.getError() : null);
        var types = result.putArray("eventTypes");
        snapshot.getEventTypesList().forEach(types::add);
        var buckets = result.putArray("buckets");
        for (var bucket : snapshot.getBucketsList()) {
            var row = buckets.addObject().put("startTime", bucket.getStartTime()).put("endTime", bucket.getEndTime())
                    .put("eventCount", bucket.getEventCount()).put("distinctEventTypes", bucket.getDistinctEventTypes())
                    .put("omittedTypes", bucket.getOmittedTypes());
            var counts = row.putArray("eventTypes");
            for (var type : bucket.getEventTypesList()) {
                counts.addObject().put("eventType", type.getEventType()).put("count", type.getCount());
            }
        }
        result.put("coverage", "Finished files visible at scan start. Overlapping recordings may count events more than once. "
                + "Counts reflect recorded events, not equivalent workloads. Incomplete scans are lower bounds; their rankings may change.");
        result.put("timeSemantics", "UTC epoch milliseconds, start inclusive and end exclusive; buckets anchored at startTime.");
        String json = Json.toString(result);
        while (json.length() > McpToolOutput.MAX_CHARS && !buckets.isEmpty()) {
            buckets.remove(buckets.size() - 1);
            result.put("omittedBuckets", snapshot.getOmittedBuckets() + snapshot.getBucketsCount() - buckets.size());
            json = Json.toString(result);
        }
        return new McpToolResult(json, result);
    }
}
