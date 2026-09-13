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

package cafe.jeffrey.hub.core.mcp;

import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.shared.common.Json;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Direct Hub tools: files remain on the Hub and only aggregate counts leave it. */
public final class HubActivityMcpTools {
    private static final int MAX_FILTER_LENGTH = 4096;
    private static final long DEFAULT_BUCKET_SECONDS = 300;
    private static final int DEFAULT_RESULT_BUCKETS = 20;
    private static final long MILLIS_PER_SECOND = 1000;
    private final HubActivityService service;

    public HubActivityMcpTools(HubActivityService service) {
        this.service = service;
    }

    @Tool(description = "Start a background scan of finished JFR files in one Hub session. Counts every matching event by time bucket "
            + "without downloading recordings or creating a Microscope profile. Returns scanId; poll hub_activityStatus until terminal. "
            + "No total-event cap. At most 288 buckets and 512 observed types; capacity or source failures make counts incomplete. "
            + "Each call starts a new scan. IDs are process-local, retained up to one hour; only the latest 16 scans are retained.")
    @McpToolHints(idempotent = false)
    public McpToolResult eventActivity(
            @ToolParam(description = "Exact Hub workspace ID") String workspaceId,
            @ToolParam(description = "Exact project ID within the workspace") String projectId,
            @ToolParam(description = "Exact recording session ID within the project") String sessionId,
            @ToolParam(description = "Inclusive start epoch milliseconds (UTC)") long startTime,
            @ToolParam(description = "Exclusive end epoch milliseconds (UTC)") long endTime,
            @ToolParam(description = "Bucket width in seconds; default 300 (5 minutes). Choose a width giving at most 288 buckets", required = false) Long bucketSeconds,
            @ToolParam(description = "Comma-separated exact event types; omit to count all types. At most 16 filters", required = false) String eventTypes) {
        if (eventTypes != null && eventTypes.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException("eventTypes is too long");
        }
        Set<String> types = eventTypes == null || eventTypes.isBlank() ? Set.of()
                : Arrays.stream(eventTypes.split(",", -1)).map(String::trim).collect(Collectors.toSet());
        long width;
        try {
            width = Math.multiplyExact(bucketSeconds == null ? DEFAULT_BUCKET_SECONDS : bucketSeconds, MILLIS_PER_SECOND);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("bucketSeconds is too large", e);
        }
        String id = service.start(new ActivityRequest(workspaceId, projectId, sessionId, startTime, endTime, width, types));
        return result(id, "events", DEFAULT_RESULT_BUCKETS);
    }

    @Tool(description = "Read a Hub event-activity scan. order=events ranks event counts; order=types ranks distinct observed event types; "
            + "order=time is chronological. Counts are lower bounds until complete=true. Output shows at most 20 buckets and 10 types per "
            + "bucket, with explicit omitted counts; totals and distinct-type counts include omitted details. Polling never restarts a scan.")
    public McpToolResult activityStatus(
            @ToolParam(description = "scanId returned by hub_eventActivity") String scanId,
            @ToolParam(description = "events (default), types or time", required = false) @ToolParamValues({"events", "types", "time"}) String order,
            @ToolParam(description = "Maximum buckets returned, 1–20; default 20", required = false) Integer limit) {
        return result(scanId, order == null ? "events" : order, limit == null ? DEFAULT_RESULT_BUCKETS : limit);
    }

    @Tool(description = "Cancel one exact Hub activity scan. cancel_requested remains nonterminal until the reader releases its resources. "
            + "Partial counts remain available; existing recordings are unchanged.")
    @McpToolHints(readOnly = false)
    public McpToolResult activityCancel(@ToolParam(description = "scanId returned by hub_eventActivity") String scanId) {
        service.cancel(scanId);
        return result(scanId, "events", DEFAULT_RESULT_BUCKETS);
    }

    private McpToolResult result(String id, String order, int limit) {
        // Drop only complete bucket detail rows. Counter totals and omission metadata stay intact.
        for (int selected = limit; selected >= 1; selected--) {
            var result = service.status(id, order, selected);
            String json = Json.toString(result);
            if (json.length() <= McpToolOutput.MAX_CHARS) {
                return new McpToolResult(json, result);
            }
        }
        throw new IllegalArgumentException("limit must be 1–20");
    }
}
