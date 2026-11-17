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

import java.util.List;

/**
 * The events of one site path and one grouping value, as the database aggregated them: how many, how long,
 * and how they spread over the traces they belong to.
 *
 * @param pathId           the path {@link CallSiteReader#pathOf} charged their stacks to
 * @param groupValue       the value of the field the read groups by, cut to {@link #MAX_GROUP_VALUE_CHARS}
 *                         characters; null when it groups by nothing or the events carried no such field
 * @param groupValueChars  the value's full length; null with the value
 * @param groupKey         what tells two values apart past the cut (a digest of the whole value); null with
 *                         the value
 * @param count            how many events
 * @param totalNanos       their summed duration, in nanoseconds; 0 for instant events
 * @param maxNanos         the longest of them, in nanoseconds
 * @param traces           how many distinct traces issued them; 0 for events outside traces
 * @param perTraceMax      the most events one trace issued
 * @param perTraceMedian   the median events per trace, among the traces that issued any
 * @param exemplarTraceIds the traces that issued most, most first, at most {@link #MAX_EXEMPLARS}
 */
public record CallSiteRow(
        int pathId,
        String groupValue,
        Integer groupValueChars,
        String groupKey,
        long count,
        long totalNanos,
        long maxNanos,
        int traces,
        long perTraceMax,
        double perTraceMedian,
        List<Long> exemplarTraceIds) {

    /**
     * How much of a grouping value a read hands over: enough to name a statement or recognise its SQL. The
     * rest is counted, not carried — a whole SQL text per group was held in memory before an answer cut it.
     */
    public static final int MAX_GROUP_VALUE_CHARS = 200;

    /** How many of the busiest traces a row names. */
    public static final int MAX_EXEMPLARS = 3;

    public CallSiteRow {
        exemplarTraceIds = exemplarTraceIds == null ? List.of() : List.copyOf(exemplarTraceIds);
    }
}
