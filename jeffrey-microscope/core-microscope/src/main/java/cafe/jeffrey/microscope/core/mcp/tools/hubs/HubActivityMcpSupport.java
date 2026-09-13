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

import cafe.jeffrey.microscope.core.manager.EventStreamingManager;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.grpc.client.ActivityScanQuery;
import cafe.jeffrey.microscope.grpc.client.ActivityScanRequest;
import cafe.jeffrey.microscope.grpc.client.ActivityScanSnapshot;
import cafe.jeffrey.microscope.grpc.client.ActivityScanTarget;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.activity.ActivityLimits;
import cafe.jeffrey.shared.common.activity.ActivityOrder;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Converts Hub summaries to bounded MCP results using the existing strict Hub resolver. */
public final class HubActivityMcpSupport {

    /**
     * The one shape all three activity tools answer with, so a client that has read it once can parse
     * a start, a poll and a cancellation alike.
     */
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","properties":{
              "sessionRef":{"type":"string"},"scanId":{"type":"string"},"operationId":{"type":"string"},
              "status":{"type":"string","enum":["queued","running","cancel_requested","completed","cancelled","failed"]},
              "startedAt":{"type":"integer"},"finishedAt":{"type":["integer","null"]},
              "complete":{"type":"boolean"},"coverageKnown":{"type":"boolean"},
              "sourceErrors":{"type":"integer"},"filesTotal":{"type":"integer"},
              "error":{"type":["string","null"]},
              "startTime":{"type":"integer"},"endTime":{"type":"integer"},"bucketMillis":{"type":"integer"},
              "requestedEventTypes":{"type":"array","items":{"type":"string"}},
              "totalEvents":{"type":"integer"},"distinctEventTypes":{"type":"integer"},
              "totalBuckets":{"type":"integer"},"order":{"type":"string","enum":["events","types","time"]},
              "offset":{"type":"integer"},"omittedBuckets":{"type":"integer"},
              "hasMoreBuckets":{"type":"boolean"},
              "buckets":{"type":"array","items":{"type":"object","properties":{
                "startTime":{"type":"integer"},"endTime":{"type":"integer"},
                "eventCount":{"type":"integer"},"distinctEventTypes":{"type":"integer"},
                "omittedTypes":{"type":"integer"},
                "eventTypes":{"type":"array","items":{"type":"object","properties":{
                  "eventType":{"type":"string"},"count":{"type":"integer"}
                },"required":["eventType","count"]}}
              },"required":["startTime","endTime","eventCount","distinctEventTypes","omittedTypes","eventTypes"]}},
              "coverage":{"type":"string"},"timeSemantics":{"type":"string"}
            },"required":["sessionRef","scanId","operationId","status","startedAt","finishedAt","complete",
              "coverageKnown","sourceErrors","filesTotal","error","startTime","endTime","bucketMillis",
              "requestedEventTypes","totalEvents","distinctEventTypes","totalBuckets","order","offset",
              "omittedBuckets","hasMoreBuckets","buckets","coverage","timeSemantics"]}
            """;

    private static final int MAX_REF_LENGTH = 2048;
    private static final int MAX_FILTER_LENGTH = 4096;

    /** The Hub's failure text is its own; it is quoted back, so it cannot be trusted to be short. */
    private static final int MAX_ERROR_LENGTH = 2000;

    private static final String OPERATION_KIND = "hub_activity";
    private static final String TYPE_SEPARATOR = ",";

    private static final String COVERAGE_NOTE =
            "Finished files visible at scan start. Overlapping recordings may count events more than once. "
                    + "Counts reflect recorded events, not equivalent workloads. "
                    + "Incomplete scans are lower bounds; their rankings may change.";

    private static final String TIME_NOTE =
            "UTC epoch milliseconds, start inclusive and end exclusive; buckets anchored at startTime.";

    private final ProjectManagerResolver resolver;
    private final McpOperationRegistry operations;
    private final Duration timeout;
    private final ScheduledExecutorService deadlines;

    public HubActivityMcpSupport(
            ProjectManagerResolver resolver,
            McpOperationRegistry operations,
            Duration timeout,
            ScheduledExecutorService deadlines) {
        this.resolver = resolver;
        this.operations = operations;
        this.timeout = timeout;
        this.deadlines = deadlines;
    }

    public McpToolResult start(
            String sessionRef,
            long startTime,
            long endTime,
            Long bucketSeconds,
            String eventTypes) {

        var ref = reference(sessionRef);
        if (eventTypes != null && eventTypes.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException("eventTypes is too long");
        }
        Set<String> types = eventTypes == null || eventTypes.isBlank() ? Set.of()
                : Arrays.stream(eventTypes.split(TYPE_SEPARATOR, -1))
                        .map(String::trim)
                        .collect(Collectors.toSet());

        // The request record enforces the same window, bucket and filter bounds the Hub does, so a
        // rejection is explained here instead of arriving as a remote INVALID_ARGUMENT.
        var request = new ActivityScanRequest(
                ref.workspaceId(),
                ref.projectId(),
                ref.sessionId(),
                startTime,
                endTime,
                bucketSeconds == null ? ActivityLimits.DEFAULT_BUCKET_SECONDS : bucketSeconds,
                types);

        var snapshot = call(ref, manager -> manager.startActivity(request));
        register(ref, snapshot);
        return result(ref, snapshot);
    }

    public McpToolResult status(String sessionRef, String scanId, String order, Integer limit, Integer offset) {
        var ref = reference(sessionRef);
        var query = new ActivityScanQuery(
                target(ref, scanId),
                ActivityOrder.fromCode(order),
                limit == null ? ActivityLimits.MAX_RESULT_BUCKETS : limit,
                offset == null ? 0 : offset);

        return result(ref, call(ref, manager -> manager.getActivity(query)));
    }

    public McpToolResult cancel(String sessionRef, String scanId) {
        var ref = reference(sessionRef);
        var target = target(ref, scanId);
        return result(ref, call(ref, manager -> manager.cancelActivity(target)));
    }

    /**
     * Makes the scan visible to {@code operations_status} and {@code operations_cancel} under its own
     * scan ID. Registration is best effort: failing to list an operation must not fail the scan that
     * is already running on the Hub.
     */
    private void register(HubSessionRef ref, ActivityScanSnapshot snapshot) {
        var target = new ActivityScanTarget(ref.workspaceId(), ref.projectId(), ref.sessionId(), snapshot.scanId());
        var query = new ActivityScanQuery(target, ActivityOrder.EVENTS, ActivityLimits.MAX_RESULT_BUCKETS, 0);
        var handle = new HubActivityOperation(
                snapshot,
                () -> call(ref, manager -> manager.getActivity(query)),
                () -> call(ref, manager -> manager.cancelActivity(target)));
        try {
            operations.registerIfRetained(OPERATION_KIND, handle, scan -> scan);
        } catch (RuntimeException e) {
            // Nothing to recover: hubs_activityStatus still reads the scan directly.
        }
    }

    private ActivityScanSnapshot call(
            HubSessionRef ref,
            Function<EventStreamingManager, ActivityScanSnapshot> operation) {

        // The only deadline on these calls. The client sets none of its own, so this is the timeout
        // that applies rather than the shorter of two.
        Deadline deadline = Deadline.after(timeout.toNanos(), TimeUnit.NANOSECONDS);
        Context.CancellableContext context = Context.current().withDeadline(deadline, deadlines);
        try {
            Callable<ActivityScanSnapshot> call = () -> operation.apply(resolver
                    .resolveStrict(ref.hubId(), ref.workspaceId(), ref.projectId())
                    .projectManager()
                    .eventStreamingManager());

            ActivityScanSnapshot snapshot = context.call(call);
            if (!ref.workspaceId().equals(snapshot.workspaceId())
                    || !ref.projectId().equals(snapshot.projectId())
                    || !ref.sessionId().equals(snapshot.sessionId())) {
                throw new IllegalStateException("Hub returned activity for a different session");
            }
            return snapshot;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(explain(e), e);
        } finally {
            context.cancel(null);
        }
    }

    private static String explain(Exception e) {
        return switch (Status.fromThrowable(e).getCode()) {
            case UNIMPLEMENTED -> "This Hub does not support event activity summaries; upgrade the Hub";
            case NOT_FOUND -> "Unknown or expired scan, or unknown session, on the Hub: " + e.getMessage();
            case DEADLINE_EXCEEDED -> "Hub activity request timed out; a started scan may still be running";
            case RESOURCE_EXHAUSTED -> "The Hub is already holding its maximum number of scans; "
                    + "cancel one with hubs_activityCancel or wait for one to finish";
            default -> "Hub activity request failed: " + e.getMessage();
        };
    }

    private static HubSessionRef reference(String sessionRef) {
        if (sessionRef == null || sessionRef.length() > MAX_REF_LENGTH) {
            throw new IllegalArgumentException(
                    "Pass a complete session_ref from hubs_sessions (at most " + MAX_REF_LENGTH + " characters)");
        }
        return HubSessionRef.decode(sessionRef);
    }

    /** The Hub wrote this text, so it is quoted back at a length this result can afford. */
    private static String shortened(String error) {
        return error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH) + "… (truncated to " + MAX_ERROR_LENGTH + " characters)";
    }

    private static ActivityScanTarget target(HubSessionRef ref, String scanId) {
        return new ActivityScanTarget(ref.workspaceId(), ref.projectId(), ref.sessionId(), scanId);
    }

    private static McpToolResult result(HubSessionRef ref, ActivityScanSnapshot snapshot) {
        ObjectNode result = Json.createObject()
                .put("sessionRef", ref.encode())
                .put("scanId", snapshot.scanId())
                .put("operationId", snapshot.scanId())
                .put("status", snapshot.status().code())
                .put("startedAt", snapshot.startedAt())
                .put("complete", snapshot.complete())
                .put("coverageKnown", snapshot.coverageKnown())
                .put("sourceErrors", snapshot.sourceErrors())
                .put("filesTotal", snapshot.filesTotal())
                .put("startTime", snapshot.startTime())
                .put("endTime", snapshot.endTime())
                .put("bucketMillis", snapshot.bucketMillis())
                .put("totalEvents", snapshot.totalEvents())
                .put("distinctEventTypes", snapshot.distinctEventTypes())
                .put("totalBuckets", snapshot.totalBuckets())
                .put("offset", snapshot.offset())
                .put("hasMoreBuckets", snapshot.hasMoreBuckets())
                .put("order", snapshot.order().code());

        if (snapshot.finishedAt() == null) {
            result.putNull("finishedAt");
        } else {
            result.put("finishedAt", snapshot.finishedAt());
        }
        if (snapshot.error() == null) {
            result.putNull("error");
        } else {
            result.put("error", shortened(snapshot.error()));
        }

        // Named for what it is: the filter the scan was started with, not the types it observed.
        ArrayNode types = result.putArray("requestedEventTypes");
        snapshot.eventTypes().forEach(types::add);

        ArrayNode buckets = result.putArray("buckets");
        for (var bucket : snapshot.buckets()) {
            ObjectNode row = buckets.addObject()
                    .put("startTime", bucket.startTime())
                    .put("endTime", bucket.endTime())
                    .put("eventCount", bucket.eventCount())
                    .put("distinctEventTypes", bucket.distinctEventTypes())
                    .put("omittedTypes", bucket.omittedTypes());

            ArrayNode counts = row.putArray("eventTypes");
            for (var type : bucket.eventTypes()) {
                counts.addObject()
                        .put("eventType", type.eventType())
                        .put("count", type.count());
            }
        }
        result.put("omittedBuckets", snapshot.omittedBuckets());
        result.put("coverage", COVERAGE_NOTE);
        result.put("timeSemantics", TIME_NOTE);

        return fit(result, buckets, snapshot);
    }

    /**
     * Drops whole buckets until the result fits, keeping {@code omittedBuckets} and
     * {@code hasMoreBuckets} truthful as it goes — a bucket removed for size is indistinguishable from
     * one left out by the page, and both are the caller's cue to narrow the window or page on.
     *
     * <p>If even a bucketless result is too large the text falls back to {@link McpToolOutput#json},
     * which caps it and says so, and the structured object is dropped rather than handed to
     * {@link McpToolResult} for it to reject.</p>
     */
    private static McpToolResult fit(ObjectNode result, ArrayNode buckets, ActivityScanSnapshot snapshot) {
        String json = Json.toString(result);
        while (json.length() > McpToolOutput.MAX_CHARS && !buckets.isEmpty()) {
            buckets.remove(buckets.size() - 1);
            result.put("omittedBuckets", snapshot.omittedBuckets() + snapshot.buckets().size() - buckets.size());
            result.put("hasMoreBuckets", true);
            json = Json.toString(result);
        }
        if (json.length() > McpToolOutput.MAX_CHARS) {
            return McpToolResult.text(McpToolOutput.json(result));
        }
        return new McpToolResult(json, result);
    }
}
