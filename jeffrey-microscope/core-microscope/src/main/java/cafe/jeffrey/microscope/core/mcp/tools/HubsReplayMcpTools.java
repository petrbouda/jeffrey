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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubActivityMcpSupport;
import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubReplayCollector;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.grpc.client.ReplaySubscriptionRequest;
import cafe.jeffrey.microscope.grpc.client.StreamingCallbacks;
import cafe.jeffrey.shared.common.Json;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.profile.mcp.McpOutputSchema;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A finite read-only query over finished Hub recording files. */
public final class HubsReplayMcpTools {
    private static final ScheduledExecutorService DEADLINES = deadlineScheduler();
    private final ProjectManagerResolver resolver;
    private final Duration timeout;
    private final HubActivityMcpSupport activity;

    public HubsReplayMcpTools(ProjectManagerResolver resolver, McpOperationRegistry operations) {
        this(resolver, operations, Duration.ofSeconds(15));
    }

    public HubsReplayMcpTools(
            ProjectManagerResolver resolver,
            McpOperationRegistry operations,
            Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Replay timeout must be positive and no greater than 30 seconds");
        }
        this.resolver = resolver;
        this.timeout = timeout;
        this.activity = new HubActivityMcpSupport(resolver, operations, timeout, DEADLINES);
    }

    @McpToolHints(readOnly = true, openWorld = true)
    @Tool(description = "Query selected JFR events from a Hub session without downloading or analysing it. "
            + "Bounded to 15 seconds, at most 1000 rows and 100000 UTF-8 bytes. "
            + "Coverage is finished files visible when replay starts; inspect complete and termination.")
    @McpOutputSchema("""
            {"type":"object","properties":{
              "sessionRef":{"type":"string"},"events":{"type":"array","items":{"type":"object"}},
              "eventTypes":{"type":"array","items":{"type":"string"}},
              "complete":{"type":"boolean"},"partial":{"type":"boolean"},"termination":{"type":"string"},
              "coverageKnown":{"type":"boolean"},"sourceErrors":{"type":"integer"},
              "rows":{"type":"integer"},"resultBytes":{"type":"integer"},
              "limit":{"type":"integer"},"maxBytes":{"type":"integer"}
            },"required":["sessionRef","events","eventTypes","complete","partial","termination",
              "coverageKnown","sourceErrors","rows","resultBytes","limit","maxBytes"]}
            """)
    public McpToolResult queryEvents(
            @ToolParam(description = "Exact session_ref from hubs_sessions") String sessionRef,
            @ToolParam(description = "Comma-separated exact JFR event type names") String eventTypes,
            @ToolParam(description = "Inclusive start epoch milliseconds", required = false) Long startTime,
            @ToolParam(description = "Inclusive end epoch milliseconds", required = false) Long endTime,
            @ToolParam(description = "Maximum rows, 1–1000; default 100", required = false) Integer limit,
            @ToolParam(description = "Maximum UTF-8 JSON object bytes (excluding duplicated MCP text/structured envelope), 4096–100000; default 65536", required = false) Integer maxBytes) {
        if (sessionRef == null || sessionRef.length() > 2048) {
            throw new IllegalArgumentException("Pass a complete session_ref from hubs_sessions (at most 2048 characters)");
        }
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        if (eventTypes == null || eventTypes.isBlank() || eventTypes.length() > 2048) {
            throw new IllegalArgumentException("Specify comma-separated JFR event types (at most 2048 characters)");
        }
        Set<String> types = Arrays.stream(eventTypes.split(",", -1))
                .map(String::trim)
                .collect(Collectors.toSet());
        if (types.size() > 16 || types.stream().anyMatch(type -> type.isBlank() || type.length() > 128)) {
            throw new IllegalArgumentException("Specify 1–16 nonempty JFR event types of at most 128 characters each");
        }
        if (startTime != null && endTime != null && startTime >= endTime) {
            throw new IllegalArgumentException("startTime must be strictly before endTime");
        }
        int rows = limit == null ? 100 : limit;
        int bytes = maxBytes == null ? 65536 : maxBytes;
        if (rows < 1 || rows > 1000 || bytes < 4096 || bytes > 100000) {
            throw new IllegalArgumentException("limit must be 1–1000 and maxBytes must be 4096–100000");
        }
        HubReplayCollector collector = new HubReplayCollector(ref, rows, bytes);
        collector.filters(types, startTime, endTime);
        Deadline deadline = Deadline.after(timeout.toNanos(), TimeUnit.NANOSECONDS);
        Context.CancellableContext context = Context.current().withDeadline(deadline, DEADLINES);
        try {
            context.call(() -> {
                ProjectManager project = resolver.resolveStrict(ref.hubId(), ref.workspaceId(), ref.projectId())
                        .projectManager();
                var request = new ReplaySubscriptionRequest(
                        ref.sessionId(),
                        types,
                        startTime,
                        endTime,
                        ref.workspaceId(),
                        ref.projectId());
                var callbacks = new StreamingCallbacks(
                        collector::accept,
                        collector::streamCompleted,
                        error -> collector.stop(termination(error)));
                var subscription = project.eventStreamingManager().subscribeReplayRaw(request, callbacks);
                collector.cancellation(subscription::cancel);
                collector.await(Duration.ofNanos(Math.max(1, deadline.timeRemaining(TimeUnit.NANOSECONDS))));
                return null;
            });
        } catch (InterruptedException e) {
            collector.stop("interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            collector.stop(termination(e));
        } finally {
            context.cancel(null);
        }
        ObjectNode result = collector.result();
        return new McpToolResult(Json.toString(result), result);
    }

    @Tool(description = "Start a background event-activity scan on the Hub for a session_ref from hubs_sessions. "
            + "Hub counts matching events by time bucket without transferring recordings or raw events to Microscope. "
            + "This creates work on the Hub: it claims one of its retained scan slots, runs a reader, and decompresses "
            + "recordings to Hub scratch space. It changes no recording and no profile. "
            + "Returns scanId; poll hubs_activityStatus with the same sessionRef and scanId, or operations_status with "
            + "the scanId as its operationId. Each call starts a new scan. "
            + "No total-event cap; at most 288 buckets and 512 observed types. Scans are Hub-process-local, retained up to "
            + "one hour after completion, with at most 16 retained scans and two concurrent readers. Requires an updated Hub.")
    @McpOutputSchema(HubActivityMcpSupport.OUTPUT_SCHEMA)
    @McpToolHints(readOnly = false, idempotent = false, openWorld = true)
    public McpToolResult eventActivity(
            @ToolParam(description = "Exact session_ref from hubs_sessions") String sessionRef,
            @ToolParam(description = "Inclusive start UTC epoch milliseconds") long startTime,
            @ToolParam(description = "Exclusive end UTC epoch milliseconds") long endTime,
            @ToolParam(description = "Bucket width in seconds; default 300, at most 288 buckets", required = false) Long bucketSeconds,
            @ToolParam(description = "Comma-separated exact event types, at most 16; omit to count all types", required = false) String eventTypes) {
        return activity.start(sessionRef, startTime, endTime, bucketSeconds, eventTypes);
    }

    @Tool(description = "Read a Hub activity scan without restarting it. Rank intervals by event count (events), "
            + "distinct event types (types), or chronological time (time). Counts are lower bounds until complete=true. "
            + "At most 20 buckets per page and 10 types per bucket; totals include omitted details. "
            + "A window holds up to 288 buckets, so page with offset while hasMoreBuckets is true — with order=time the "
            + "first page can be entirely empty when the recording starts late in the window. "
            + "Use the original sessionRef and scanId.")
    @McpOutputSchema(HubActivityMcpSupport.OUTPUT_SCHEMA)
    @McpToolHints(readOnly = true, openWorld = true)
    public McpToolResult activityStatus(
            @ToolParam(description = "The session_ref used to start the scan") String sessionRef,
            @ToolParam(description = "scanId returned by hubs_eventActivity") String scanId,
            @ToolParam(description = "events (default), types or time", required = false) @ToolParamValues({"events", "types", "time"}) String order,
            @ToolParam(description = "Maximum buckets returned per page, 1–20; default 20", required = false) Integer limit,
            @ToolParam(description = "Buckets to skip in the ranked order before this page; default 0, at most 288", required = false) Integer offset) {
        return activity.status(sessionRef, scanId, order, limit, offset);
    }

    @Tool(description = "Cancel an exact activity scan on its Hub. Use the original sessionRef and scanId. "
            + "cancel_requested remains nonterminal until reader cleanup finishes. Partial counts remain available; recordings are unchanged.")
    @McpOutputSchema(HubActivityMcpSupport.OUTPUT_SCHEMA)
    @McpToolHints(readOnly = false, idempotent = true, openWorld = true)
    public McpToolResult activityCancel(
            @ToolParam(description = "The session_ref used to start the scan") String sessionRef,
            @ToolParam(description = "scanId returned by hubs_eventActivity") String scanId) {
        return activity.cancel(sessionRef, scanId);
    }

    private static String termination(Throwable error) {
        return switch (Status.fromThrowable(error).getCode()) {
            case DEADLINE_EXCEEDED -> "timeout";
            case CANCELLED -> "cancelled";
            case NOT_FOUND -> "not_found";
            case UNAVAILABLE -> "unavailable";
            case UNIMPLEMENTED -> "unsupported_hub";
            default -> "remote_error";
        };
    }

    private static ScheduledExecutorService deadlineScheduler() {
        var factory = Thread.ofPlatform()
                .daemon()
                .name("hub-mcp-replay-deadline-", 0)
                .factory();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, factory);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
