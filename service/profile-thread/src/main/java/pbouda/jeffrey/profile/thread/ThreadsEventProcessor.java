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

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.common.ThreadInfo;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ThreadsEventProcessor implements EventProcessor<List<ThreadRecord>> {

    private final static List<Type> PROCESSABLE_TYPES = List.of(
            Type.THREAD_START,
            Type.THREAD_END,
            Type.THREAD_PARK,
            Type.JAVA_MONITOR_ENTER,
            Type.JAVA_MONITOR_WAIT);

    private final static ProcessableEvents PROCESSABLE_EVENTS = new ProcessableEvents(PROCESSABLE_TYPES);

    private final List<ThreadRecord> result = new ArrayList<>();

    public ThreadsEventProcessor() {
    }

    @Override
    public ProcessableEvents processableEvents() {
        return PROCESSABLE_EVENTS;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        Type eventType = Type.from(event.getEventType());

        ThreadRecord threadRecord;
        if (eventType == Type.THREAD_START) {
            threadRecord = resolveThreadStart(event);
        } else if (eventType == Type.THREAD_END) {
            threadRecord = resolveThreadEnd(event);
        } else if (eventType == Type.THREAD_PARK) {
            threadRecord = resolveThreadPark(event);
        } else if (eventType == Type.JAVA_MONITOR_ENTER) {
            threadRecord = resolveMonitorEnter(event);
        } else if (eventType == Type.JAVA_MONITOR_WAIT) {
            threadRecord = resolveMonitorWait(event);
        } else {
            throw new IllegalStateException("Unsupported event type: " + eventType);
        }
        result.add(threadRecord);

        return Result.CONTINUE;
    }

    private ThreadRecord resolveThreadStart(RecordedEvent event) {
        return new ThreadRecord(
                resolveThreadInfo(event.getThread("thread")),
                event.getStartTime(),
                event.getEventType().getLabel(),
                ThreadState.STARTED);
    }

    private ThreadRecord resolveThreadEnd(RecordedEvent event) {
        return new ThreadRecord(
                resolveThreadInfo(event.getThread("thread")),
                event.getStartTime(),
                event.getEventType().getLabel(),
                ThreadState.ENDED);
    }

    private ThreadRecord resolveThreadPark(RecordedEvent event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.getClass("parkedClass").getName());
        paramValues.add(parseTimeout(event));
        paramValues.add(event.getLong("until"));

        return new ThreadRecord(
                resolveThreadInfo(event),
                paramValues,
                event.getStartTime(),
                event.getEndTime(),
                event.getDuration(),
                event.getEventType().getLabel(),
                ThreadState.PARKED);
    }

    private ThreadRecord resolveMonitorEnter(RecordedEvent event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.getClass("monitorClass").getName());
        paramValues.add(safeThreadName(event.getThread("previousOwner")));

        return new ThreadRecord(
                resolveThreadInfo(event),
                paramValues,
                event.getStartTime(),
                event.getEndTime(),
                event.getDuration(),
                event.getEventType().getLabel(),
                ThreadState.BLOCKED);
    }

    private ThreadRecord resolveMonitorWait(RecordedEvent event) {
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(event.getClass("monitorClass").getName());
        paramValues.add(safeThreadName(event.getThread("notifier")));
        paramValues.add(parseTimeout(event));
        paramValues.add(event.getBoolean("timedOut"));

        return new ThreadRecord(
                resolveThreadInfo(event),
                paramValues,
                event.getStartTime(),
                event.getEndTime(),
                event.getDuration(),
                event.getEventType().getLabel(),
                ThreadState.WAITING);
    }

    private ThreadInfo resolveThreadInfo(RecordedEvent event) {
        RecordedThread thread = event.getThread();
        if (thread == null && event.hasField("sampledThread")) {
            thread = event.getThread("sampledThread");
        }
        if (thread == null) {
            return null;
        }
        return resolveThreadInfo(thread);
    }

    private ThreadInfo resolveThreadInfo(RecordedThread thread) {
        return new ThreadInfo(
                thread.getOSThreadId(),
                thread.getJavaThreadId(),
                thread.getOSName(),
                thread.getJavaName());
    }

    @Override
    public List<ThreadRecord> get() {
        return result;
    }

    private long parseTimeout(RecordedEvent event) {
        long timeout = safeDurationToLongNanos(event.getDuration("timeout"));
        return timeout <= 0 ? -1 : timeout;
    }

    private static String safeThreadName(RecordedThread thread) {
        if (thread != null) {
            return thread.getJavaName() != null ? thread.getJavaName() : thread.getOSName();
        } else {
            return null;
        }
    }

    private static long safeDurationToLongNanos(Duration value) {
        if (value.getSeconds() == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        } else {
            return safeToLongNanos(value);
        }
    }

    private static long safeToLongNanos(Duration value) {
        return value.isNegative() ? -1 : value.toNanos();
    }
}
