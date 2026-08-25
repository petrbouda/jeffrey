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
