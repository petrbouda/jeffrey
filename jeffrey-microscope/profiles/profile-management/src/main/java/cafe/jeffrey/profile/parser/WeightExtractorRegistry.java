/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.parser;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import cafe.jeffrey.shared.common.model.Type;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static cafe.jeffrey.shared.common.model.Type.*;
import java.util.Map;

public class WeightExtractorRegistry {

    /** The {@code jdk.MethodTrace} field naming the method being traced. */
    private static final String METHOD_FIELD = "method";

    /**
     * How a method is written into {@code weight_entity}, and how {@code JfrMethodImpl.of} reads it
     * back out again.
     */
    private static final String METHOD_SEPARATOR = "#";

    private static final Map<Type, WeightExtractor> REGISTRY = Collections.unmodifiableMap(buildRegistry());

    private static Map<Type, WeightExtractor> buildRegistry() {
        Map<Type, WeightExtractor> registry = new HashMap<>();
        registry.put(NATIVE_LEAK, WeightExtractor.allocation("size"));
        registry.put(METHOD_TRACE, WeightExtractor.duration(WeightExtractorRegistry::extractTracedMethod));
        registry.put(CPU_TIME_SAMPLE, WeightExtractor.durationField("samplingPeriod"));
        registry.put(MALLOC, WeightExtractor.allocation("size", e -> String.valueOf(e.getLong("address"))));
        registry.put(FREE, WeightExtractor.allocationEntityOnly(e -> String.valueOf(e.getLong("address"))));
        registry.put(JAVA_MONITOR_ENTER, WeightExtractor.duration("monitorClass"));
        registry.put(JAVA_MONITOR_WAIT, WeightExtractor.duration("monitorClass"));
        registry.put(THREAD_PARK, WeightExtractor.duration("parkedClass"));
        registry.put(THREAD_SLEEP, WeightExtractor.duration());
        // Pinned time is blocked time, and the rest of the codebase already treats it that way --
        // BlockingLeafSpans promotes it into a span, TraceContextCategory.VT_PINNED explains it as a
        // wait. Without a weight here it was the one blocking event whose flamegraph could only be
        // counted, never weighted by the time it cost. No entity: unlike a monitor or a park there
        // is no class the thread was pinned *on*, only a carrier it could not leave.
        registry.put(VIRTUAL_THREAD_PINNED, WeightExtractor.duration());
        registry.put(OBJECT_ALLOCATION_IN_NEW_TLAB, WeightExtractor.allocation("tlabSize", "objectClass"));
        registry.put(OBJECT_ALLOCATION_OUTSIDE_TLAB, WeightExtractor.allocation("allocationSize", "objectClass"));
        registry.put(OBJECT_ALLOCATION_SAMPLE, WeightExtractor.allocation("weight", "objectClass"));
        registry.put(SOCKET_READ, WeightExtractor.allocation("bytesRead"));
        registry.put(SOCKET_WRITE, WeightExtractor.allocation("bytesWritten"));
        registry.put(FILE_READ, WeightExtractor.allocation("bytesRead"));
        registry.put(FILE_WRITE, WeightExtractor.allocation("bytesWritten"));
        registry.put(THREAD_ALLOCATION_STATISTICS, WeightExtractor.allocation("allocated"));
        return registry;
    }

    public static WeightExtractor resolve(Type type) {
        return REGISTRY.get(type);
    }

    /**
     * The method a {@code jdk.MethodTrace} event was tracing, read from the event's own field.
     * <p>
     * This used to take the leaf frame of the stack trace, which is what every other stack-carrying
     * event would mean by "the method this is about". {@code jdk.MethodTrace} is the exception: JEP
     * 520 roots its stack trace at the <em>caller</em>, so the leaf frame is the method that made the
     * call, never the one being traced. On a JDK 25 recording of a probe tracing two methods, every
     * single event was mislabelled by exactly one frame — the events for {@code Probe.inner} were
     * attributed to {@code Probe.outer}, and those for {@code Probe.outer} to {@code Probe.main},
     * a method the filter had never selected and which appeared in the dashboard regardless.
     * <p>
     * Null when the field is absent rather than falling back to the frame: the fallback is precisely
     * the wrong answer, and a silently wrong method name is worse than a missing one.
     */
    /**
     * The method a {@code jdk.MethodTrace} event was recorded for.
     * <p>
     * JEP 520 puts it in a {@code method} field, and that is read first. Not every recording carries
     * one: an event written by a profiler that emits the type itself, or by a JDK that shipped the
     * event before the field, arrives with only a start time, a duration and a stack. Those events
     * are not unattributable — the traced method is the innermost frame of the stack the event was
     * taken at, by construction, because the event is written from inside that method. Falling back
     * to it is what keeps the Method Tracing dashboard from reporting a recording with real traces in
     * it as having none: without an entity every record is dropped, and the totals come back zero
     * while the events sit in the table.
     */
    private static String extractTracedMethod(RecordedEvent event) {
        if (event.hasField(METHOD_FIELD)) {
            RecordedMethod method = event.getValue(METHOD_FIELD);
            if (method != null) {
                return format(method);
            }
        }
        return topFrameMethod(event);
    }

    private static String topFrameMethod(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return null;
        }
        List<RecordedFrame> frames = stackTrace.getFrames();
        if (frames.isEmpty()) {
            return null;
        }
        RecordedMethod method = frames.getFirst().getMethod();
        return method == null ? null : format(method);
    }

    private static String format(RecordedMethod method) {
        return method.getType().getName() + METHOD_SEPARATOR + method.getName();
    }
}
