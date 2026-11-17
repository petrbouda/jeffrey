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
 * One JFR event that occurred on a given thread inside a given time window — what the JVM was doing
 * while something else was running there.
 * <p>
 * Shared by the trace drill-down and the async-profiler span drill-down. Those two remain separate
 * features answering separate questions, and that separation is deliberate; but "what ran on this
 * thread between these two instants" is not two questions. It had been two identical records, two
 * near-identical queries and two mappers, which is two places to fix whenever the answer changes.
 *
 * @param eventType        the JFR event type, e.g. {@code jdk.ExecutionSample}
 * @param startEpochMillis when it occurred, absolute UTC epoch millis
 * @param durationNanos    its duration, or 0 for an instantaneous event
 * @param fields           the event's own fields, as a JSON object string
 */
public record ThreadWindowEventRecord(
        String eventType,
        long startEpochMillis,
        long durationNanos,
        String fields) {
}
