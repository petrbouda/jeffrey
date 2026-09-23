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
 * One line of a "where did the time go" breakdown.
 *
 * @param category    a {@code TraceContextCategory} name, or {@link #OWN_WORK} for the remainder
 * @param totalNanos  how much time went to it
 * @param occurrences how many events that was; {@code 0} for the residual, which is not an event
 */
public record TraceContextSlice(String category, long totalNanos, long occurrences) {

    /**
     * The residual: time not attributable to any category, which is the code running.
     * <p>
     * Reported as a slice of its own rather than left as the gap between a total and a list of
     * parts. A breakdown that only names the waiting invites the reader to add it up and assume the
     * rest is unexplained; naming the remainder is what makes the panel an explanation instead of a
     * list of complaints.
     */
    public static final String OWN_WORK = "OWN_WORK";
}
