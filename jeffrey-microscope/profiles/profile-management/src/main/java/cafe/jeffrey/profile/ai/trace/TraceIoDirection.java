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

package cafe.jeffrey.profile.ai.trace;

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The I/O event types the trace derivation promotes into spans, and what each one's payload means.
 * <p>
 * Two things differ per event type and nothing else does: which field carries the byte count, and
 * what counts as the <em>target</em> the operation was against — a peer for a socket, a path for a
 * file. Both travel with the constant rather than being decided by a conditional at the call site,
 * so adding an event type is one line here and no change anywhere else.
 */
enum TraceIoDirection {

    SOCKET_READ(EventTypeName.SOCKET_READ, "Socket read", BytesField.READ, TraceIoDirection::socketPeer),
    SOCKET_WRITE(EventTypeName.SOCKET_WRITE, "Socket write", BytesField.WRITTEN, TraceIoDirection::socketPeer),
    FILE_READ(EventTypeName.FILE_READ, "File read", BytesField.READ, TraceIoDirection::filePath),
    FILE_WRITE(EventTypeName.FILE_WRITE, "File write", BytesField.WRITTEN, TraceIoDirection::filePath),
    /**
     * An fsync. It moves no bytes at all, which is the point of listing it beside the others: its
     * cost is the durability barrier, and many of them in one trace is a flush-per-record shape.
     */
    FILE_FORCE(EventTypeName.FILE_FORCE, "File force", BytesField.NONE, TraceIoDirection::filePath);

    /** Which JFR field holds an operation's byte count, or that the event declares none. */
    private enum BytesField {
        READ("bytesRead"),
        WRITTEN("bytesWritten"),
        NONE(null);

        private final String name;

        BytesField(String name) {
            this.name = name;
        }
    }

    private static final String HOST_FIELD = "host";
    private static final String PORT_FIELD = "port";
    private static final String PATH_FIELD = "path";
    private static final String UNKNOWN_TARGET = "<unknown>";

    /** Lookup rather than a switch ladder, so the mapping stays a table one line per event type. */
    private static final Map<String, TraceIoDirection> BY_EVENT_TYPE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TraceIoDirection::eventType, Function.identity()));

    private final String eventType;
    private final String label;
    private final BytesField bytesField;
    private final Function<JsonNode, String> targetResolver;

    TraceIoDirection(
            String eventType,
            String label,
            BytesField bytesField,
            Function<JsonNode, String> targetResolver) {

        this.eventType = eventType;
        this.label = label;
        this.bytesField = bytesField;
        this.targetResolver = targetResolver;
    }

    static Optional<TraceIoDirection> of(String eventType) {
        return Optional.ofNullable(BY_EVENT_TYPE.get(eventType));
    }

    String eventType() {
        return eventType;
    }

    String label() {
        return label;
    }

    /** Whether operations of this kind report a byte count at all — {@code false} for an fsync. */
    boolean carriesBytes() {
        return bytesField.name != null;
    }

    /**
     * How many bytes this operation moved, or {@code 0} when the recording did not say. Missing and
     * zero are folded together on purpose: the section reports sums and means, and a negative
     * sentinel leaking into either would be worse than under-counting a field the JFR did not emit.
     */
    long bytes(JsonNode fields) {
        if (!carriesBytes()) {
            return 0L;
        }
        return Math.max(0L, Json.readLong(fields, bytesField.name));
    }

    String target(JsonNode fields) {
        return targetResolver.apply(fields);
    }

    private static String socketPeer(JsonNode fields) {
        String host = Json.readString(fields, HOST_FIELD);
        if (host == null || host.isBlank()) {
            host = UNKNOWN_TARGET;
        }
        long port = Json.readLong(fields, PORT_FIELD);
        return port >= 0 ? host + ":" + port : host;
    }

    private static String filePath(JsonNode fields) {
        String path = Json.readString(fields, PATH_FIELD);
        return path == null || path.isBlank() ? UNKNOWN_TARGET : path;
    }
}
