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

package cafe.jeffrey.profile.manager.model.trace;

/**
 * One trace in the trace list.
 * <p>
 * {@code traceId} is a hex string, not a number: a 64-bit id exceeds JavaScript's safe integer
 * range, so sending it as JSON number would silently round it in the browser — the same reason
 * {@code threadHash} already crosses the wire as a string.
 */
public record TraceRow(
        String traceId,
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
