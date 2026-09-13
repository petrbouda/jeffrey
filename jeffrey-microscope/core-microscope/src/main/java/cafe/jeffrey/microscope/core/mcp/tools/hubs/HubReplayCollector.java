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

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.ReplayStatus;
import cafe.jeffrey.hub.api.v1.StreamingEvent;
import cafe.jeffrey.hub.api.v1.TypedValue;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounds one replay and preserves the first terminal reason, including cancellation races. */
public final class HubReplayCollector {
    private static final int TERMINAL_RESERVE = 512;
    /** The caller asked for every matching event; only the byte budget and the deadline bound it. */
    private static final int NO_ROW_LIMIT = 0;
    private final HubSessionRef ref;
    private final int limit;
    private final int maxBytes;
    private final ObjectNode output = Json.createObject();
    private final ArrayNode events = output.putArray("events");
    private final CompletableFuture<Void> done = new CompletableFuture<>();
    private int accumulated;
    private boolean acknowledged;
    private boolean terminal;
    private boolean cancelled;
    private Long summaryErrors;
    private Runnable cancel;

    public HubReplayCollector(HubSessionRef ref, int limit, int maxBytes) {
        this.ref = ref;
        this.limit = limit;
        this.maxBytes = maxBytes;
        output.put("sessionRef", ref.encode());
        output.put("complete", false);
        output.put("partial", true);
        output.put("termination", "running");
        output.put("coverageKnown", false);
        output.put("sourceErrors", 0);
        output.put("rows", 0);
        output.put("resultBytes", 0);
        output.put("limit", limit);
        output.put("maxBytes", maxBytes);
        output.put("coverage", "Finished files visible at replay start; overlapping files may repeat events. "
                + "No global ordering or deduplication guarantee. Stack traces are omitted.");
        output.put("fieldUnits", "Event timestamps and timestamp fields: epoch milliseconds; timespans: nanoseconds; percentages: fractions.");
        // Seeded here as well as in filters(), which is optional: a running total that started at zero
        // would under-count the identity fields by their whole size and let the answer past maxBytes.
        accumulated = bytes();
    }

    public synchronized void filters(Set<String> eventTypes, Long startTime, Long endTime) {
        ArrayNode types = output.putArray("eventTypes");
        eventTypes.stream().sorted().forEach(types::add);
        if (startTime != null) {
            output.put("startTime", startTime);
        }
        if (endTime != null) {
            output.put("endTime", endTime);
        }
        accumulated = bytes();
        if (accumulated + TERMINAL_RESERVE > maxBytes) {
            throw new IllegalArgumentException("maxBytes is too small for the requested identity and filters");
        }
    }

    public synchronized void acknowledge(String workspaceId, String projectId) {
        if (terminal) {
            return;
        }
        if (!ref.workspaceId().equals(workspaceId) || !ref.projectId().equals(projectId)) {
            stop("scope_mismatch");
            return;
        }
        acknowledged = true;
    }

    public synchronized void accept(EventBatch batch) {
        if (terminal) {
            return;
        }
        if (batch.hasReplayStatus()) {
            ReplayStatus status = batch.getReplayStatus();
            if (status.getTerminal()) {
                summaryErrors = status.getSourceErrors();
            } else {
                acknowledge(status.getWorkspaceId(), status.getProjectId());
            }
        }
        for (StreamingEvent event : batch.getEventsList()) {
            if (terminal) {
                return;
            }
            if (!acknowledged) {
                stop("unsupported_hub");
                return;
            }
            if (!ref.sessionId().equals(event.getSessionId())) {
                stop("scope_mismatch");
                return;
            }
            // Truncation is declared by the event that does not fit, never by the last one that did.
            // Stopping on the limit-th row instead would report a query whose every match was
            // returned as partial, and would skip the coverage summary that completed() records --
            // so a caller asking for exactly as many rows as the session holds would be told, with
            // coverageKnown false, to narrow a window that has nothing left to give.
            if (limit != NO_ROW_LIMIT && events.size() == limit) {
                stop("row_limit");
                return;
            }
            ObjectNode row = eventJson(event);
            // What this row costs, rather than what the whole answer now weighs. Re-serialising the
            // document once per event is quadratic in the row count, including when the caller
            // disables the row limit. Rows are independent array elements, so the delta is exact:
            // the row itself, the comma before it, and any digit the row counter grows by.
            int delta = utf8Length(Json.toString(row))
                    + (events.isEmpty() ? 0 : 1)
                    + digits(events.size() + 1) - digits(events.size());
            if (accumulated + delta + TERMINAL_RESERVE > maxBytes) {
                stop("byte_limit");
                return;
            }
            events.add(row);
            accumulated += delta;
            output.put("rows", events.size());
        }
    }

    public synchronized void streamCompleted() {
        if (summaryErrors == null) {
            stop(acknowledged ? "missing_coverage" : "unsupported_hub");
        } else {
            completed(summaryErrors);
        }
    }

    public synchronized void completed(long sourceErrors) {
        if (terminal) {
            return;
        }
        if (!acknowledged) {
            stop("unsupported_hub");
            return;
        }
        output.put("coverageKnown", true);
        output.put("sourceErrors", sourceErrors);
        stop(sourceErrors == 0 ? "completed" : "source_errors");
    }

    public synchronized void stop(String reason) {
        if (terminal) {
            return;
        }
        terminal = true;
        output.put("termination", reason);
        output.put("complete", reason.equals("completed"));
        output.put("partial", !reason.equals("completed"));
        done.complete(null);
        cancelIfFinished();
    }

    public synchronized void cancellation(Runnable cancel) {
        this.cancel = cancel;
        cancelIfFinished();
    }

    private void cancelIfFinished() {
        if (terminal && !cancelled && cancel != null) {
            cancelled = true;
            cancel.run();
        }
    }

    public void await(Duration remaining) throws InterruptedException {
        try {
            done.get(Math.max(1, remaining.toNanos()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            stop("timeout");
        } catch (ExecutionException e) {
            throw new IllegalStateException("Replay completion failed", e.getCause());
        }
    }

    public synchronized ObjectNode result() {
        // resultBytes includes its own decimal representation; converge after a few digits change.
        for (int attempt = 0; attempt < 3; attempt++) {
            output.put("resultBytes", bytes());
        }
        return output.deepCopy();
    }

    private int bytes() {
        return utf8Length(Json.toString(output));
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int digits(int value) {
        return Integer.toString(value).length();
    }

    private static ObjectNode eventJson(StreamingEvent event) {
        ObjectNode row = Json.createObject();
        row.put("eventType", event.getEventType());
        row.put("sessionId", event.getSessionId());
        row.put("timestamp", event.getTimestamp());
        ObjectNode fields = row.putObject("fields");
        event.getFieldsMap().forEach((name, value) -> putValue(fields, name, value));
        return row;
    }

    private static void putValue(ObjectNode fields, String name, TypedValue value) {
        switch (value.getValueCase()) {
            case STRING_VALUE -> fields.put(name, value.getStringValue());
            case LONG_VALUE -> fields.put(name, value.getLongValue());
            case DOUBLE_VALUE -> fields.put(name, value.getDoubleValue());
            case FLOAT_VALUE -> fields.put(name, value.getFloatValue());
            case BOOL_VALUE -> fields.put(name, value.getBoolValue());
            case VALUE_NOT_SET -> fields.putNull(name);
        }
    }
}
