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
