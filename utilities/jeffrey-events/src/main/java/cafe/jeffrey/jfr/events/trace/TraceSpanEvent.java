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

package cafe.jeffrey.jfr.events.trace;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * One span in a trace: a named interval of work, linked to its parent by
 * {@link AbstractTracedEvent#parentSpanId}.
 * <p>
 * It declares no fields of its own. Everything a span is made of — its name, kind, status, error
 * type and attributes — lives on {@link AbstractTracedEvent}, which every instrumented event now
 * carries, so a hand-written span is simply the case where the span shape is all there is. What
 * remains here is the event type: the way to record an interval that no other instrumentation
 * already describes.
 * <p>
 * JFR supplies the parts that would otherwise have to be recorded by hand — the start timestamp,
 * the duration and the thread — so the event only has to carry what makes the interval meaningful.
 * <p>
 * Every span is recorded, however short. A duration threshold is a decision about one application's
 * span volume, not something this event should make on its behalf: dropping short spans orphans
 * their children and moves their samples into the parent's self time. Set one explicitly per
 * recording when the volume calls for it, through the usual JFR configuration
 * ({@code -XX:StartFlightRecording:cafe.jeffrey.jfr.events.trace.TraceSpanEvent#threshold=1ms}).
 * <p>
 * The declared template is the identity, {@code {name}}: a hand-written span is named by whoever
 * opened it, and {@link Tracer} records that name before the event commits. The declaration keeps
 * the invariant that every span type this library ships carries its convention in the recording.
 * No {@code @SpanOutcome}: the outcome is written by {@link Tracer} as a span status directly,
 * there is no code field to judge.
 *
 * @see Tracer
 */
@Name(TraceSpanEvent.NAME)
@Label("Trace Span")
@Description("A single named interval of work within a trace")
@Category({"Application", "Tracing"})
@StackTrace(false)
@SpanName("{name}")
public class TraceSpanEvent extends AbstractTracedEvent {

    public static final String NAME = "jeffrey.TraceSpan";
}
