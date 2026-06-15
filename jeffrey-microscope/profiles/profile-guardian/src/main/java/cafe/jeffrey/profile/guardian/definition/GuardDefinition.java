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

package cafe.jeffrey.profile.guardian.definition;

import cafe.jeffrey.profile.guardian.guard.Guard;
import cafe.jeffrey.profile.guardian.traverse.MatchingType;
import cafe.jeffrey.profile.guardian.traverse.ResultType;
import cafe.jeffrey.profile.guardian.traverse.TargetFrameType;

/**
 * The complete, data-driven definition of a single Guardian guard. Replaces the hard-coded guard
 * subclasses: a {@code ConfigurableGuard} is constructed entirely from one of these records, which is
 * loaded from the central microscope-core database (and editable from the UI).
 *
 * @param guardId          stable identifier (UUID for custom guards, a fixed slug for built-ins)
 * @param name             display name shown in the Guardian results table
 * @param enabled          whether the guard participates in analysis
 * @param builtIn          true for shipped defaults, false for user-created guards
 * @param eventType        the JFR event type whose stacktraces form the frame tree (e.g. {@code jdk.ExecutionSample})
 * @param category         UI grouping bucket
 * @param resultType       how the matched subtree is measured (samples / weight / self-*)
 * @param targetFrameType  whether the matcher walks Java, JVM/native, or all frames
 * @param matchingType     stop at the first match or accumulate all matches
 * @param infoThreshold    ratio above which severity flips to INFO
 * @param warningThreshold ratio above which severity flips to WARNING
 * @param minSamples       minimum samples the event type must have before the guard runs
 * @param anchor           generic predicate tree locating the anchor frame
 * @param traversal        how observed frames are reached from the anchor
 * @param preconditions    applicability constraints (event source, GC type, symbols)
 * @param summaryNoun      the variable noun rendered into the summary sentence
 * @param explanation      static explanation text (HTML)
 * @param solution         static solution text (HTML), shown only for non-OK severities
 */
public record GuardDefinition(
        String guardId,
        String name,
        boolean enabled,
        boolean builtIn,
        String eventType,
        Guard.Category category,
        ResultType resultType,
        TargetFrameType targetFrameType,
        MatchingType matchingType,
        double infoThreshold,
        double warningThreshold,
        long minSamples,
        MatchExpr anchor,
        TraversalStrategy traversal,
        GuardPreconditions preconditions,
        String summaryNoun,
        String explanation,
        String solution) {

    public GuardDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Guard name must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Guard event type must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Guard category must not be null");
        }
        if (resultType == null) {
            throw new IllegalArgumentException("Guard result type must not be null");
        }
        if (targetFrameType == null) {
            throw new IllegalArgumentException("Guard target frame type must not be null");
        }
        if (matchingType == null) {
            throw new IllegalArgumentException("Guard matching type must not be null");
        }
        if (anchor == null) {
            throw new IllegalArgumentException("Guard anchor matcher must not be null");
        }
        if (traversal == null) {
            traversal = TraversalStrategy.CURRENT_FRAME;
        }
        if (preconditions == null) {
            preconditions = GuardPreconditions.NONE;
        }
    }
}
