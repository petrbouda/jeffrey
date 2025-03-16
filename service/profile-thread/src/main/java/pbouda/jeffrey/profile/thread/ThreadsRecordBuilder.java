/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.thread;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.model.ThreadInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ThreadsRecordBuilder implements RecordBuilder<GenericRecord, List<ThreadRecord>> {

    private final List<ThreadRecord> result = new ArrayList<>();

    public ThreadsRecordBuilder() {
    }

    @Override
    public void onRecord(GenericRecord record) {
        Type eventType = record.type();

        ThreadRecord threadRecord;
        if (eventType == Type.THREAD_START) {
            threadRecord = resolveThreadEvent(record, ThreadState.STARTED);
        } else if (eventType == Type.THREAD_END) {
            threadRecord = resolveThreadEvent(record, ThreadState.ENDED);
        } else if (eventType == Type.THREAD_PARK) {
            threadRecord = resolveThreadPark(record);
        } else if (eventType == Type.THREAD_SLEEP) {
            threadRecord = resolveThreadSleep(record);
        } else if (eventType == Type.JAVA_MONITOR_ENTER) {
            threadRecord = resolveMonitorEnter(record);
        } else if (eventType == Type.JAVA_MONITOR_WAIT) {
            threadRecord = resolveMonitorWait(record);
        } else if (eventType == Type.SOCKET_READ) {
            threadRecord = resolveSocketRead(record);
        } else if (eventType == Type.SOCKET_WRITE) {
            threadRecord = resolveSocketWrite(record);
        } else if (eventType == Type.FILE_READ) {
            threadRecord = resolveFileRead(record);
        } else if (eventType == Type.FILE_WRITE) {
            threadRecord = resolveFileWrite(record);
        } else {
            throw new IllegalStateException("Unsupported event type: " + eventType);
        }
        result.add(threadRecord);
    }

    private ThreadRecord resolveThreadEvent(GenericRecord event, ThreadState state) {
        JfrThread thread = event.thread();
        return new ThreadRecord(
                new ThreadInfo(thread.osThreadId(), thread.javaThreadId(), thread.name()),
                event.timestampFromStart(),
                event.typeLabel(),
                state);
    }

    private ThreadRecord resolveThreadPark(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToString(event, "parkedClass"));
        paramValues.add(safeToLong(event, "timeout"));
        paramValues.add(safeToLong(event, "until"));

        return toThreadRecord(event, paramValues, ThreadState.PARKED);
    }

    private ThreadRecord resolveThreadSleep(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToLong(event, "time"));

        return toThreadRecord(event, paramValues, ThreadState.SLEEP);
    }

    private ThreadRecord resolveMonitorEnter(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToString(event, "monitorClass"));
        paramValues.add(safeToString(event, "previousOwner"));

        return toThreadRecord(event, paramValues, ThreadState.BLOCKED);
    }

    private ThreadRecord resolveMonitorWait(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToString(event, "monitorClass"));
        paramValues.add(safeToString(event, "notifier"));
        paramValues.add(safeToLong(event, "timeout"));
        paramValues.add(safeToBoolean(event, "timedOut"));

        return toThreadRecord(event, paramValues, ThreadState.WAITING);
    }

    private ThreadRecord resolveSocketRead(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToString(event, "host"));
        paramValues.add(safeToString(event, "address"));
        paramValues.add(safeToLong(event, "port"));
        paramValues.add(safeToLong(event, "timeout"));
        paramValues.add(safeToLong(event, "bytesRead"));
        paramValues.add(safeToBoolean(event, "endOfStream"));

        return toThreadRecord(event, paramValues, ThreadState.SOCKET_READ);
    }

    private ThreadRecord resolveSocketWrite(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToString(event, "host"));
        paramValues.add(safeToString(event, "address"));
        paramValues.add(safeToLong(event, "port"));
        paramValues.add(safeToLong(event, "bytesWritten"));

        return toThreadRecord(event, paramValues, ThreadState.SOCKET_WRITE);
    }

    private ThreadRecord resolveFileRead(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToString(event, "path"));
        paramValues.add(safeToLong(event, "bytesRead"));
        paramValues.add(safeToBoolean(event, "endOfFile"));

        return toThreadRecord(event, paramValues, ThreadState.FILE_READ);
    }

    private ThreadRecord resolveFileWrite(GenericRecord event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.duration().toNanos());
        paramValues.add(safeToString(event, "path"));
        paramValues.add(safeToLong(event, "bytesWritten"));

        return toThreadRecord(event, paramValues, ThreadState.FILE_WRITE);
    }

    @Override
    public List<ThreadRecord> build() {
        return result;
    }

    private static ThreadRecord toThreadRecord(GenericRecord event, List<Object> params, ThreadState state) {
        JfrThread thread = event.thread();
        return new ThreadRecord(
                new ThreadInfo(thread.osThreadId(), thread.javaThreadId(), thread.name()),
                params,
                event.timestampFromStart(),
                event.duration(),
                event.typeLabel(),
                state);
    }

    private static String safeToString(GenericRecord event, String fieldName) {
        return safeBySupplier(event.jsonFields().get(fieldName), JsonNode::asText);
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
