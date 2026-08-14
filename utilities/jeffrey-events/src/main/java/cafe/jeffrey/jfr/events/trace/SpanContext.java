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

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Where a span sits in its trace: the trace it belongs to, its own id, and its parent's.
 * <p>
 * Immutable by design — a nested span never mutates its parent's context, it derives a new one via
 * {@link #child(RandomGenerator)}. That is what makes the context safe to publish through a
 * {@link java.lang.ScopedValue}.
 * <p>
 * Because the context carries the parent id as well as its own, it fully describes a span's
 * position, which is what lets {@link Tracer#stamp(AbstractTracedEvent)} fill in an event in one
 * step.
 *
 * @param traceId      identifies the whole trace; shared by every span within it
 * @param spanId       identifies this span; unique within the trace
 * @param parentSpanId the enclosing span's id, or {@code 0} when this span is a root
 */
public record SpanContext(long traceId, long spanId, long parentSpanId) {

    /**
     * Starts a new trace: a fresh trace id, a fresh span id, and no parent. Ids are drawn from the
     * calling thread's {@link ThreadLocalRandom}.
     */
    public static SpanContext root() {
        return root(ThreadLocalRandom.current());
    }

    /**
     * The form of {@link #root()} that takes the generator explicitly, for tests that need
     * deterministic ids.
     */
    public static SpanContext root(RandomGenerator random) {
        return new SpanContext(nonZero(random), nonZero(random), 0);
    }

    /**
     * Derives a child of this span — same trace, new span id, parented to this one. The id is drawn
     * from the calling thread's {@link ThreadLocalRandom}.
     */
    public SpanContext child() {
        return child(ThreadLocalRandom.current());
    }

    /**
     * The form of {@link #child()} that takes the generator explicitly, for tests that need
     * deterministic ids.
     */
    public SpanContext child(RandomGenerator random) {
        return new SpanContext(traceId, nonZero(random), spanId);
    }

    /**
     * @return whether this span starts a trace rather than continuing one
     */
    public boolean isRoot() {
        return parentSpanId == 0;
    }

    /**
     * Ids use {@code 0} to mean "absent", so a generated id must never be zero. The retry is
     * effectively free: the odds of drawing zero are one in 2^64.
     */
    private static long nonZero(RandomGenerator random) {
        long value;
        do {
            value = random.nextLong();
        } while (value == 0);
        return value;
    }
}
