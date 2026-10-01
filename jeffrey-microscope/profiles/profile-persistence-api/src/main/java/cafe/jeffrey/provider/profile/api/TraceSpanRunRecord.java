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
 * What the members of one folded run share: every leaf under one parent with the same name, event
 * type and I/O origin, once there are enough of them to fold. Their numbers — how many, how long,
 * where — come from {@link TraceSpanShape}s tagged with {@link #runId()}.
 *
 * @param runId          the run's identity: its lowest member span id
 * @param parentSpanId   the members' stored parent, or {@code null} for a run of roots
 * @param name           the members' shared name
 * @param kind           the members' kind
 * @param eventType      the members' shared event type
 * @param ioOrigin       the members' shared I/O origin, or {@code null}
 * @param synthesized    whether every member was synthesized from a JDK event
 * @param firstThreadName the name of the thread the earliest member ran on, when known
 */
public record TraceSpanRunRecord(
        long runId,
        Long parentSpanId,
        String name,
        String kind,
        String eventType,
        String ioOrigin,
        boolean synthesized,
        String firstThreadName) {
}
