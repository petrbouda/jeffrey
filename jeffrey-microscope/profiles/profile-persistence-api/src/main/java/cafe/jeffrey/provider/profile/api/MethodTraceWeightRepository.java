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

package cafe.jeffrey.provider.profile.api;

/**
 * Settles what a {@code jdk.MethodTrace} event contributes when its durations are summed.
 *
 * <p>Every other stack-carrying event measures something disjoint: a sample is one sample, an
 * allocation is its own bytes, a park is time the thread was not running. A method trace measures a
 * call, and <b>a call's duration includes its callees</b> (JEP 520). The moment a filter matches two
 * methods on one stack — a class-wide filter over a method and the helper it calls is enough —
 * summing durations counts the inner call once for itself and again inside every traced method above
 * it, and a flamegraph reports a root that ran twice as long as the recording did.
 *
 * <p>So the two numbers an event carries are given distinct jobs, which is the split the rest of the
 * schema already assumes:
 * <ul>
 *   <li>{@code duration} stays the call's own latency, inclusive of callees. This is what the Method
 *       Tracing dashboard ranks and averages, and what a span promoted from the event spans.</li>
 *   <li>{@code weight} becomes the call's <b>self</b> time — its duration minus the time its nested
 *       traced calls already account for — because weight is what every aggregate sums, and only an
 *       exclusive quantity may be summed across nested measurements.</li>
 * </ul>
 *
 * <p>Nothing is lost by the subtraction: the inner call still carries its own time, one level down,
 * where a flamegraph puts it back under the caller as a child. Totals reconstitute; only the
 * double-count goes away. With no nesting anywhere — the common single-method filter — every self
 * time equals its duration and this changes nothing.
 */
public interface MethodTraceWeightRepository {

    /**
     * Rewrites every {@code jdk.MethodTrace} weight to the call's self time. Idempotent by
     * construction: it recomputes each weight from {@code duration}, which it never writes, so a
     * second run lands where the first did.
     *
     * @return how many events were given a self time below their duration, i.e. how many traced
     * calls turned out to contain another
     */
    int deriveSelfWeights();

    /**
     * Whether the profile holds a single {@code jdk.MethodTrace} event.
     * <p>
     * Almost no recording does — the events exist only for methods someone named in a JFR filter —
     * so callers use this to skip the pass rather than pay a scan to update nothing.
     */
    boolean hasMethodTraces();
}
