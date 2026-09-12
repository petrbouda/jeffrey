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
    public HubsReplayMcpTools(ProjectManagerResolver resolver) {
        this(resolver, Duration.ofSeconds(15));
    }
    public HubsReplayMcpTools(ProjectManagerResolver resolver, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Replay timeout must be positive and no greater than 30 seconds");
        }
        this.resolver = resolver;
        this.timeout = timeout;
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
        Set<String> types = Arrays.stream(eventTypes.split(",", -1)).map(String::trim).collect(Collectors.toSet());
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
                ProjectManager project = resolver.resolveStrict(ref.hubId(), ref.workspaceId(), ref.projectId()).projectManager();
                var request = new ReplaySubscriptionRequest(ref.sessionId(), types, startTime, endTime,
                        ref.workspaceId(), ref.projectId());
                var subscription = project.eventStreamingManager().subscribeReplayRaw(request,
                        new StreamingCallbacks(collector::accept, collector::streamCompleted,
                                error -> collector.stop(termination(error))));
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
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().daemon().name("hub-mcp-replay-deadline-", 0).factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
