/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.model.Type;


import java.util.List;
import java.util.Optional;

public interface ProfileEventRepository {

    Optional<ObjectNode> latestJsonFields(Type type);

    List<AllocatingThread> allocatingThreads(int limit);

    List<JsonNode> eventsByTypeWithFields(Type type);

    /**
     * Aggregate statistics over the {@code duration} column for events of the given type.
     * Returns {@link EventDurationStats#EMPTY} when no events exist or none carry a duration.
     * Consumed by guards that reason about time distributions (e.g. safepoint p99 outliers,
     * virtual-thread pin total time).
     */
    EventDurationStats durationStatsByType(Type type);

    /**
     * How many {@code jdk.CPUTimeSample} events the profile holds and how many the JVM reported as
     * dropped. Returns {@link CpuTimeSampleLoss#EMPTY} when the CPU-time sampler was not used.
     */
    CpuTimeSampleLoss cpuTimeSampleLoss();

    boolean containsEventType(Type type);

    /**
     * Retrieves JVM flags related to String handling from JFR flag events.
     * Queries jdk.BooleanFlag, jdk.IntFlag, and jdk.UnsignedIntFlag events
     * for flags related to string deduplication, GC, and string representation.
     *
     * @return list of JVM flags related to string handling
     */
    List<JvmFlag> getStringRelatedFlags();

    /**
     * Retrieves all JVM flags from JFR flag events with full details.
     * Queries all flag event types (BooleanFlag, IntFlag, UnsignedIntFlag, LongFlag, StringFlag)
     * and includes change detection to show if flag values changed during the recording.
     *
     * @return list of all JVM flags with their latest values and change indicators
     */
    List<JvmFlagDetail> getAllFlags();
}
