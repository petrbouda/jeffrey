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
