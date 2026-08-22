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

package cafe.jeffrey.jfr.events.test;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

import java.util.List;

/**
 * One span read out of a recording, whatever event type carried it.
 * <p>
 * Spans are discovered <em>structurally</em>, by the declared {@code spanId} field, which is the
 * same rule Jeffrey's own derivation uses: an event type instrumented later takes part in traces
 * with no change here. A {@code jeffrey.TraceScope} declares {@code scopedSpanId} instead and is
 * therefore correctly not a span.
 *
 * @param eventType the JFR event name that carried the span, e.g. {@code jeffrey.JdbcQuery}
 * @param traceId   the whole trace's id; {@code 0} means the event is not part of any trace
 * @param spanId    this span's id; {@code 0} means the event was committed unstamped
 * @param parentSpanId the enclosing span's id, or {@code 0} for a root
 * @param name      the operation name
 * @param kind      {@code INTERNAL} | {@code SERVER} | {@code CLIENT}, as recorded
 * @param status    {@code UNSET} | {@code OK} | {@code ERROR}, as recorded
 * @param errorType the class name of the exception that ended the span, when it failed
 * @param threadName the thread JFR attributed the event to, i.e. the one that committed it
 */
public record RecordedSpan(
        String eventType,
        long traceId,
        long spanId,
        long parentSpanId,
        String name,
        String kind,
        String status,
        String errorType,
        String threadName) {

    /** The field whose presence makes an event a span. */
    static final String SPAN_ID = "spanId";

    private static final String TRACE_ID = "traceId";
    private static final String PARENT_SPAN_ID = "parentSpanId";
    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String STATUS = "status";
    private static final String ERROR_TYPE = "errorType";

    /**
     * @return whether this event declares the span shape, i.e. takes part in traces at all
     */
    public static boolean isSpan(RecordedEvent event) {
        return event.hasField(SPAN_ID);
    }

    /**
     * Reads the spans out of a recording's events, skipping everything that is not one.
     */
    public static List<RecordedSpan> from(List<RecordedEvent> events) {
        return events.stream()
                .filter(RecordedSpan::isSpan)
                .map(RecordedSpan::of)
                .toList();
    }

    private static RecordedSpan of(RecordedEvent event) {
        RecordedThread thread = event.getThread();
        return new RecordedSpan(
                event.getEventType().getName(),
                longField(event, TRACE_ID),
                longField(event, SPAN_ID),
                longField(event, PARENT_SPAN_ID),
                stringField(event, NAME),
                stringField(event, KIND),
                stringField(event, STATUS),
                stringField(event, ERROR_TYPE),
                thread == null ? null : thread.getJavaName());
    }

    /**
     * @return whether this span starts a trace rather than continuing one
     */
    public boolean isRoot() {
        return parentSpanId == 0;
    }

    /**
     * @return whether the span carries trace identity at all; {@code false} is the signature of an
     * event committed with a bare {@code commit()} instead of {@code commitSpan()}
     */
    public boolean isTraced() {
        return traceId != 0 && spanId != 0;
    }

    private static long longField(RecordedEvent event, String field) {
        return event.hasField(field) ? event.getLong(field) : 0;
    }

    private static String stringField(RecordedEvent event, String field) {
        return event.hasField(field) ? event.getString(field) : null;
    }
}
