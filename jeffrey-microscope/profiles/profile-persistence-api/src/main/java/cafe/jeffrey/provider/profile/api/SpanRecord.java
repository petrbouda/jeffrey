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
 * A single {@code profiler.Span} event read from the profile {@code events} table — a latency
 * interval on one thread, tagged with an operation name.
 *
 * @param startMillisFromBeginning span start, in milliseconds relative to the first event of the recording
 * @param startEpochMillis         span start as an absolute UTC epoch-millis timestamp
 * @param durationNanos            span duration in nanoseconds (0 if the span had no duration)
 * @param threadHash               hash of the enclosing thread's identity — the reliable join key for
 *                                 pairing the span with the events on the same thread (works for
 *                                 platform <em>and</em> virtual threads, unlike the OS id which is
 *                                 absent for virtual threads)
 * @param osThreadId               OS thread id of the enclosing thread (0 if unknown, e.g. virtual threads)
 * @param javaThreadId             Java thread id (0 if unknown)
 * @param threadName               thread name (may be {@code null})
 * @param isVirtual                whether the enclosing thread is a virtual thread
 * @param tag                      span tag (may be {@code null})
 */
public record SpanRecord(
        long startMillisFromBeginning,
        long startEpochMillis,
        long durationNanos,
        long threadHash,
        long osThreadId,
        long javaThreadId,
        String threadName,
        boolean isVirtual,
        String tag) {
}
