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
 * One throw recorded inside a trace, as the UI reads it.
 *
 * @param spanId       the innermost span open on its thread when it was thrown. Never null: a throw
 *                     with no containing span is not part of the trace at all, and is not derived
 * @param exceptionId  identifies it within the trace
 * @param eventType    {@code jdk.JavaExceptionThrow} or {@code jdk.JavaErrorThrow}
 * @param thrownClass  the class that was thrown
 * @param escaped      whether this throw is why its span failed. What lets the span's bare
 *                     {@code errorType} be shown with a message, an instant and a stack behind it
 * @param stacktraceId reference into the recording's stack traces, or {@code null} when none was
 *                     captured — which is what decides whether the drill-down offers a stack at all
 */
public record TraceExceptionRow(
        String spanId,
        String exceptionId,
        long startMillisFromBeginning,
        long startEpochMicros,
        String eventType,
        String thrownClass,
        String message,
        boolean escaped,
        String stacktraceId,
        String threadHash) {
}
