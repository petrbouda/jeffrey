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

package cafe.jeffrey.profile.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Aggregate statistics for jdk.Deoptimization events. Exposed on the page header / stats row.
 *
 * @param totalCount     total number of deoptimization events
 * @param distinctMethods number of distinct methods that experienced a deopt
 * @param distinctReasons number of distinct deopt reasons observed
 * @param topReason      most frequent reason value (null if no events)
 * @param topReasonCount events with the top reason
 * @param topMethod      most frequent method (null if no events)
 * @param topMethodCount events for the top method
 * @param c1Count        events emitted by the C1 compiler
 * @param c2Count        events emitted by the C2 compiler
 * @param recordingDurationMillis duration of the recording window
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JITDeoptimizationStats(
        long totalCount,
        long distinctMethods,
        long distinctReasons,
        String topReason,
        long topReasonCount,
        String topMethod,
        long topMethodCount,
        long c1Count,
        long c2Count,
        long recordingDurationMillis) {
}
