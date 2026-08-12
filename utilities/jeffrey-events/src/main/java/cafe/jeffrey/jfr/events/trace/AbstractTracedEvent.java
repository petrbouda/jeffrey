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

import jdk.jfr.Contextual;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * Trace identity carried by every event that can take part in a trace.
 * <p>
 * The ids are plain {@code long}s rather than strings on purpose: JFR varint-encodes integral
 * fields, while every distinct string value enters the per-chunk constant pool. Trace and span ids
 * are the highest-cardinality values an event can carry, so encoding them as strings is the single
 * biggest recording-size risk in a tracing setup.
 * <p>
 * A value of {@code 0} means "absent" — zero is also the cheapest varint encoding, so an event that
 * never takes part in a trace costs practically nothing over its untraced shape. A span with
 * {@code parentSpanId == 0} is a root span.
 * <p>
 * The trace id is 64-bit, not the 128-bit shape used by W3C Trace Context and OpenTelemetry.
 * Jeffrey mints every id itself within a single recording, where 64 bits is far more than enough;
 * the trade-off is that an externally supplied 128-bit trace id cannot be stored without loss.
 * <p>
 * {@link Contextual} on the id fields does nothing for Jeffrey's own analysis — it reconstructs the
 * span-to-event association from the thread and the time window. It is there so that {@code jfr
 * print} and JDK Mission Control show the trace and span id next to every lock, I/O and exception
 * event that occurred inside the span, for anyone opening the recording in another tool.
 */
public abstract class AbstractTracedEvent extends Event {

    @Label("Trace Id")
    @Contextual
    public long traceId;

    @Label("Span Id")
    @Contextual
    public long spanId;

    @Label("Parent Span Id")
    public long parentSpanId;
}
