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
import jdk.jfr.Threshold;

/**
 * One span in a trace: a named interval of work, linked to its parent by
 * {@link AbstractTracedEvent#parentSpanId}.
 * <p>
 * JFR supplies the parts that would otherwise have to be recorded by hand — the start timestamp,
 * the duration and the thread — so the event only has to carry what makes the interval meaningful.
 * <p>
 * The default {@link Threshold} keeps trivially short spans out of the recording. It can be lowered
 * or removed per recording through the usual JFR configuration
 * ({@code -XX:StartFlightRecording:cafe.jeffrey.jfr.events.trace.TraceSpanEvent#threshold=0ms}).
 *
 * @see Tracer
 */
@Name(TraceSpanEvent.NAME)
@Label("Trace Span")
@Description("A single named interval of work within a trace")
@Category({"Application", "Tracing"})
@StackTrace(false)
@Threshold("1 ms")
public class TraceSpanEvent extends AbstractTracedEvent {

    public static final String NAME = "jeffrey.TraceSpan";

    @Label("Name")
    @Description("Operation name; must be a stable, low-cardinality label")
    public String name;

    @Label("Kind")
    public String kind;

    @Label("Status")
    public String status;

    @Label("Error Type")
    @Description("Class name of the exception that ended the span, when it ended in an error")
    public String errorType;

    @Label("Attributes")
    @Description("Operation-specific detail, encoded as a JSON object")
    public String attributes;
}
