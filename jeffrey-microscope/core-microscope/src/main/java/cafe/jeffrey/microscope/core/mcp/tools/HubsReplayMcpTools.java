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
import cafe.jeffrey.shared.common.activity.ActivityLimits;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.profile.mcp.McpOutputSchema;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Clock;
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

    /** Seconds rather than a Duration, because the tool description quotes the number and must be a constant expression. */
    private static final long DEFAULT_TIMEOUT_SECONDS = 15;
    private static final long MAX_TIMEOUT_SECONDS = 30;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(MAX_TIMEOUT_SECONDS);

    /** Rows handed back when the caller names no limit. */
    private static final int DEFAULT_ROW_LIMIT = 100;

    /** The limit value that means no row limit at all; bytes and the deadline still bound the answer. */
    private static final int NO_ROW_LIMIT = 0;

    private static final int DEFAULT_MAX_BYTES = 65536;
    private static final int MIN_MAX_BYTES = 4096;
    private static final int MAX_MAX_BYTES = 100000;

    private static final int MAX_SESSION_REF_LENGTH = 2048;
    private static final int MAX_EVENT_TYPES_LENGTH = 2048;

    /**
     * The same caps {@code hubs_eventActivity} applies to its event-type filter, so the two tools
     * agree on what a type name may look like.
     */
    private static final int MAX_EVENT_TYPES = ActivityLimits.MAX_FILTER_TYPES;
    private static final int MAX_EVENT_TYPE_LENGTH = ActivityLimits.MAX_TYPE_LENGTH;

    private static final String EVENT_TYPE_SEPARATOR = ",";
    private static final String TERMINATION_INTERRUPTED = "interrupted";
    private final ProjectManagerResolver resolver;
    private final Duration timeout;
    private final HubActivityMcpSupport activity;

    public HubsReplayMcpTools(ProjectManagerResolver resolver, McpOperationRegistry operations, Clock clock) {
        this(resolver, operations, DEFAULT_TIMEOUT, clock);
    }

    public HubsReplayMcpTools(
            ProjectManagerResolver resolver,
            McpOperationRegistry operations,
            Duration timeout,
            Clock clock) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "Replay timeout must be positive and no greater than " + MAX_TIMEOUT_SECONDS + " seconds");
        }
        this.resolver = resolver;
        this.timeout = timeout;
        this.activity = new HubActivityMcpSupport(resolver, operations, timeout, DEADLINES, clock);
    }

    @McpToolHints(readOnly = true, openWorld = true)
    @Tool(description = "Query selected JFR events from a Hub session without downloading or analysing it. "
            + "Returns the first matching events in replay order, not ranked by duration: default "
            + DEFAULT_ROW_LIMIT + " rows. Set a positive limit for more rows or " + NO_ROW_LIMIT + " for no row limit. "
            + "The " + DEFAULT_TIMEOUT_SECONDS + "-second deadline and maxBytes budget still apply: default "
            + DEFAULT_MAX_BYTES + ", maximum " + MAX_MAX_BYTES + " UTF-8 bytes. "
            + "How many events fit depends on their serialized size; raising limit does not raise maxBytes. "
            + "Narrow eventTypes and the time window when a limit is reached. "
            + "Coverage is finished files visible when replay starts; inspect complete and termination.")
    @McpOutputSchema("""
            {"type":"object","properties":{
              "sessionRef":{"type":"string"},"events":{"type":"array","items":{"type":"object"}},
              "eventTypes":{"type":"array","items":{"type":"string"}},
              "complete":{"type":"boolean"},"partial":{"type":"boolean"},"termination":{"type":"string"},
              "coverageKnown":{"type":"boolean"},"sourceErrors":{"type":"integer"},
              "error":{"type":"string","description":"The remote failure message, present only when the Hub or the transport failed"},
              "rows":{"type":"integer"},"resultBytes":{"type":"integer"},
              "limit":{"type":"integer","minimum":0,"description":"Requested row limit; 0 means no row limit"},
              "maxBytes":{"type":"integer"}
            },"required":["sessionRef","events","eventTypes","complete","partial","termination",
              "coverageKnown","sourceErrors","rows","resultBytes","limit","maxBytes"]}
            """)
    public McpToolResult queryEvents(
            @ToolParam(description = "Exact session_ref from hubs_sessions") String sessionRef,
            @ToolParam(description = "Comma-separated exact JFR event type names") String eventTypes,
            @ToolParam(description = "Inclusive start epoch milliseconds", required = false) Long startTime,
            @ToolParam(description = "Inclusive end epoch milliseconds", required = false) Long endTime,
            @ToolParam(description = "Maximum rows; default " + DEFAULT_ROW_LIMIT + ". Any positive integer, or "
                    + NO_ROW_LIMIT + " for no row limit. Byte and time limits still apply", required = false) Integer limit,
            @ToolParam(description = "Maximum UTF-8 JSON object bytes (excluding duplicated MCP text/structured envelope), "
                    + MIN_MAX_BYTES + "–" + MAX_MAX_BYTES + "; default " + DEFAULT_MAX_BYTES, required = false) Integer maxBytes) {
        if (sessionRef == null || sessionRef.length() > MAX_SESSION_REF_LENGTH) {
            throw new IllegalArgumentException("Pass a complete session_ref from hubs_sessions (at most "
                    + MAX_SESSION_REF_LENGTH + " characters)");
        }
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        if (eventTypes == null || eventTypes.isBlank() || eventTypes.length() > MAX_EVENT_TYPES_LENGTH) {
            throw new IllegalArgumentException("Specify comma-separated JFR event types (at most "
                    + MAX_EVENT_TYPES_LENGTH + " characters)");
        }
        Set<String> types = Arrays.stream(eventTypes.split(EVENT_TYPE_SEPARATOR, -1))
                .map(String::trim)
                .collect(Collectors.toSet());
        if (types.size() > MAX_EVENT_TYPES
                || types.stream().anyMatch(type -> type.isBlank() || type.length() > MAX_EVENT_TYPE_LENGTH)) {
            throw new IllegalArgumentException("Specify 1–" + MAX_EVENT_TYPES + " nonempty JFR event types of at most "
                    + MAX_EVENT_TYPE_LENGTH + " characters each");
        }
        if (startTime != null && endTime != null && startTime >= endTime) {
            throw new IllegalArgumentException("startTime must be strictly before endTime");
        }
        int rows = limit == null ? DEFAULT_ROW_LIMIT : limit;
        int bytes = maxBytes == null ? DEFAULT_MAX_BYTES : maxBytes;
        if (rows < NO_ROW_LIMIT) {
            throw new IllegalArgumentException("limit must be nonnegative; " + NO_ROW_LIMIT + " means no row limit");
        }
        if (bytes < MIN_MAX_BYTES || bytes > MAX_MAX_BYTES) {
            throw new IllegalArgumentException("maxBytes must be " + MIN_MAX_BYTES + "–" + MAX_MAX_BYTES);
        }
        HubReplayCollector collector = new HubReplayCollector(ref, rows, bytes);
        collector.filters(types, startTime, endTime);
        // Resolved before the deadline context, not inside it: the resolver is local, and an unknown
        // hub, workspace or project is the caller's mistake. Inside the context every exception was
        // read as a remote failure, so a typo in the session_ref came back as rows: 0 and
        // termination: remote_error with the message that named the typo thrown away.
        ProjectManager project = resolver.resolveStrict(ref.hubId(), ref.workspaceId(), ref.projectId())
                .projectManager();
        Deadline deadline = Deadline.after(timeout.toNanos(), TimeUnit.NANOSECONDS);
        Context.CancellableContext context = Context.current().withDeadline(deadline, DEADLINES);
        try {
            context.call(() -> {
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
                        error -> collector.stop(termination(error), describe(error)));
                var subscription = project.eventStreamingManager().subscribeReplayRaw(request, callbacks);
                collector.cancellation(subscription::cancel);
                collector.await(Duration.ofNanos(Math.max(1, deadline.timeRemaining(TimeUnit.NANOSECONDS))));
                return null;
            });
        } catch (InterruptedException e) {
            collector.stop(TERMINATION_INTERRUPTED);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            collector.stop(termination(e), describe(e));
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
            + "the scanId as its operationId. An identical call repeated while its scan is still running on the Hub "
            + "adopts that scan instead of claiming a second slot; once it has finished, the same call starts a new one. "
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

    /**
     * The message worth quoting back for a failure: the gRPC status description when there is one,
     * otherwise whatever the exception says, and at the very least the status code's name.
     */
    private static String describe(Throwable error) {
        Status status = Status.fromThrowable(error);
        if (status.getDescription() != null && !status.getDescription().isBlank()) {
            return status.getDescription();
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        return status.getCode().name();
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
