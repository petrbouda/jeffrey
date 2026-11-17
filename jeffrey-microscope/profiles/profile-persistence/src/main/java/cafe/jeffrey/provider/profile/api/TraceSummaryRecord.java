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
 * One trace, summarised for the trace list — enough to decide which trace is worth opening,
 * without reading its spans.
 *
 * @param traceId                    identifies the trace; rendered as 16-char hex in the UI
 * @param rootName                   operation name of the trace's root span
 * @param rootKind                   {@code SERVER}, {@code CLIENT} or {@code INTERNAL}
 * @param rootEventType              the event type that opened the trace, e.g.
 *                                   {@code jeffrey.HttpServerExchange} — which instrumentation it
 *                                   came from, something the name alone does not say
 * @param startMillisFromBeginning   trace start, in milliseconds relative to the recording's start
 * @param startEpochMillis           trace start as an absolute UTC epoch-millis timestamp
 * @param durationNanos              wall time from the first span's start to the last span's end
 * @param spanCount                  how many spans the trace contains
 * @param errorCount                 how many of them ended in an error
 * @param hasPlatformSpan            whether any of its spans ran on a platform thread, which is what
 *                                   decides whether a sample can ever be attributed to this trace
 */
public record TraceSummaryRecord(
        long traceId,
        String rootName,
        String rootKind,
        String rootEventType,
        long startMillisFromBeginning,
        long startEpochMillis,
        long durationNanos,
        int spanCount,
        int errorCount,
        boolean hasPlatformSpan) {
}
