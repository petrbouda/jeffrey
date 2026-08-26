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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Counts the events of one category inside a hovered window, and keeps a few of them for the
 * tooltip's field rows.
 *
 * <p>Every streamed record is counted, but only the first {@code limit} are turned into a
 * {@link ThreadEventDetail}. That matters: reading the fields means pulling the event's JSON apart,
 * and a window on a busy lane can still hold thousands of events. Counting is what the tooltip
 * needs from all of them; the fields it only needs from one.
 */
public class ThreadWindowEventsBuilder implements RecordBuilder<GenericRecord, ThreadWindowEvents> {

    /**
     * Field values per state, positional and in the order {@link ThreadMetadata} declares them. A
     * map rather than a conditional ladder, so a state and its fields are declared in one place.
     */
    private static final Map<ThreadState, Function<GenericRecord, List<Object>>> FIELDS_BY_STATE = Map.of(
            ThreadState.PARKED, ThreadWindowEventsBuilder::parkedFields,
            ThreadState.SLEEP, ThreadWindowEventsBuilder::sleepFields,
            ThreadState.BLOCKED, ThreadWindowEventsBuilder::blockedFields,
            ThreadState.WAITING, ThreadWindowEventsBuilder::waitingFields,
            ThreadState.SOCKET_READ, ThreadWindowEventsBuilder::socketReadFields,
            ThreadState.SOCKET_WRITE, ThreadWindowEventsBuilder::socketWriteFields,
            ThreadState.FILE_READ, ThreadWindowEventsBuilder::fileReadFields,
            ThreadState.FILE_WRITE, ThreadWindowEventsBuilder::fileWriteFields);

    private static final String PARKED_CLASS_FIELD = "parkedClass";
    private static final String TIMEOUT_FIELD = "timeout";
    private static final String UNTIL_FIELD = "until";
    private static final String TIME_FIELD = "time";
    private static final String MONITOR_CLASS_FIELD = "monitorClass";
    private static final String PREVIOUS_OWNER_FIELD = "previousOwner";
    private static final String NOTIFIER_FIELD = "notifier";
    private static final String TIMED_OUT_FIELD = "timedOut";
    private static final String HOST_FIELD = "host";
    private static final String ADDRESS_FIELD = "address";
    private static final String PORT_FIELD = "port";
    private static final String BYTES_READ_FIELD = "bytesRead";
    private static final String BYTES_WRITTEN_FIELD = "bytesWritten";
    private static final String END_OF_STREAM_FIELD = "endOfStream";
    private static final String PATH_FIELD = "path";
    private static final String END_OF_FILE_FIELD = "endOfFile";

    /**
     * An instantaneous event still has to be wide enough to exist on the timeline.
     */
    private static final long MIN_WIDTH_NANOS = 1;

    private final long fromOffset;
    private final long toOffset;
    private final int limit;

    private final List<ThreadEventDetail> events = new ArrayList<>();
    private long eventCount;

    public ThreadWindowEventsBuilder(RelativeTimeRange window, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }
        this.fromOffset = window.start().toNanos();
        this.toOffset = window.end().toNanos();
        this.limit = limit;
    }

    @Override
    public void onRecord(GenericRecord record) {
        eventCount++;
        if (events.size() >= limit) {
            return;
        }

        ThreadState state = ThreadState.fromEventType(record.type());
        Function<GenericRecord, List<Object>> fields = FIELDS_BY_STATE.get(state);
        if (fields == null) {
            throw new IllegalStateException("Thread state carries no event fields: " + state);
        }

        events.add(new ThreadEventDetail(
                record.timestampFromStart().toNanos(), widthNanos(record), fields.apply(record)));
    }

    @Override
    public ThreadWindowEvents build() {
        return new ThreadWindowEvents(fromOffset, toOffset, eventCount, events);
    }

    private static List<Object> parkedFields(GenericRecord event) {
        return fieldValues(
                durationNanos(event),
                safeToString(event, PARKED_CLASS_FIELD),
                safeToLong(event, TIMEOUT_FIELD),
                safeToLong(event, UNTIL_FIELD));
    }

    private static List<Object> sleepFields(GenericRecord event) {
        return fieldValues(durationNanos(event), safeToLong(event, TIME_FIELD));
    }

    private static List<Object> blockedFields(GenericRecord event) {
        return fieldValues(
                durationNanos(event),
                safeToString(event, MONITOR_CLASS_FIELD),
                safeToString(event, PREVIOUS_OWNER_FIELD));
    }

    private static List<Object> waitingFields(GenericRecord event) {
        return fieldValues(
                durationNanos(event),
                safeToString(event, MONITOR_CLASS_FIELD),
                safeToString(event, NOTIFIER_FIELD),
                safeToLong(event, TIMEOUT_FIELD),
                safeToBoolean(event, TIMED_OUT_FIELD));
    }

    private static List<Object> socketReadFields(GenericRecord event) {
        return fieldValues(
                durationNanos(event),
                safeToString(event, HOST_FIELD),
                safeToString(event, ADDRESS_FIELD),
                safeToLong(event, PORT_FIELD),
                safeToLong(event, TIMEOUT_FIELD),
                safeToLong(event, BYTES_READ_FIELD),
                safeToBoolean(event, END_OF_STREAM_FIELD));
    }

    private static List<Object> socketWriteFields(GenericRecord event) {
        return fieldValues(
                durationNanos(event),
                safeToString(event, HOST_FIELD),
                safeToString(event, ADDRESS_FIELD),
                safeToLong(event, PORT_FIELD),
                safeToLong(event, BYTES_WRITTEN_FIELD));
    }

    private static List<Object> fileReadFields(GenericRecord event) {
        return fieldValues(
                durationNanos(event),
                safeToString(event, PATH_FIELD),
                safeToLong(event, BYTES_READ_FIELD),
                safeToBoolean(event, END_OF_FILE_FIELD));
    }

    private static List<Object> fileWriteFields(GenericRecord event) {
        return fieldValues(
                durationNanos(event), safeToString(event, PATH_FIELD), safeToLong(event, BYTES_WRITTEN_FIELD));
    }

    /**
     * Keeps nulls, unlike {@link List#of}: a field the recording did not capture is a gap the
     * tooltip renders as a dash, and dropping it would shift every later value onto the wrong label.
     */
    private static List<Object> fieldValues(Object... values) {
        List<Object> fields = new ArrayList<>(values.length);
        Collections.addAll(fields, values);
        return fields;
    }

    private static long widthNanos(GenericRecord event) {
        Duration duration = event.duration();
        if (duration == null) {
            return MIN_WIDTH_NANOS;
        }
        return Math.max(duration.toNanos(), MIN_WIDTH_NANOS);
    }

    private static Long durationNanos(GenericRecord event) {
        Duration duration = event.duration();
        if (duration == null) {
            return null;
        }
        return duration.toNanos();
    }

    private static String safeToString(GenericRecord event, String fieldName) {
        return safeBySupplier(event.jsonFields().get(fieldName), JsonNode::asString);
    }

    private static Long safeToLong(GenericRecord event, String fieldName) {
        return safeBySupplier(event.jsonFields().get(fieldName), JsonNode::asLong);
    }

    private static Boolean safeToBoolean(GenericRecord event, String fieldName) {
        return safeBySupplier(event.jsonFields().get(fieldName), JsonNode::asBoolean);
    }

    private static <T> T safeBySupplier(JsonNode value, Function<JsonNode, T> valueExtractor) {
        if (value != null && !value.isNull()) {
            return valueExtractor.apply(value);
        } else {
            return null;
        }
    }
}
