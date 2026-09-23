/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
