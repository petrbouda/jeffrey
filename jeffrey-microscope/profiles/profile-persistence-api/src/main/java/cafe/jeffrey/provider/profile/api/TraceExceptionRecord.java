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
 * One throw that happened inside a trace.
 * <p>
 * Unlike a notification, a throw carries no ids of its own and needs none: it is always recorded on
 * the thread that threw it, at the instant it threw, so the span is found by taking the innermost
 * window containing that instant on that thread.
 *
 * @param traceId                  the trace it happened in
 * @param spanId                   the innermost span open on its thread when it was thrown
 * @param exceptionId              identifies this throw within the trace
 * @param startMillisFromBeginning when it was thrown, relative to the recording's start
 * @param startEpochMicros         when it was thrown, as absolute UTC epoch micros
 * @param eventType                {@code jdk.JavaExceptionThrow} or {@code jdk.JavaErrorThrow} —
 *                                 which is how an Error is told from an Exception without guessing
 *                                 from the class name
 * @param thrownClass              the class that was thrown
 * @param message                  the throwable's message, when it had one
 * @param escaped                  whether this throw is why its span failed, decided by matching
 *                                 {@link #thrownClass()} against that span's own error type. False
 *                                 for a throw caught inside the span, which is most of them
 * @param stacktraceHash           reference into the stacktraces table, or {@code null} when the
 *                                 recording captured no stack for it
 * @param threadHash               identity hash of the throwing thread
 */
public record TraceExceptionRecord(
        long traceId,
        long spanId,
        long exceptionId,
        long startMillisFromBeginning,
        long startEpochMicros,
        String eventType,
        String thrownClass,
        String message,
        boolean escaped,
        Long stacktraceHash,
        long threadHash) {
}
