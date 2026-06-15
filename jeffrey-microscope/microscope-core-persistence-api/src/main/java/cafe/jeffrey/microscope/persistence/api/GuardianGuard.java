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

package cafe.jeffrey.microscope.persistence.api;

import java.time.Instant;

/**
 * One row of the {@code guardians} table — the raw, persistence-level view of a Guardian guard
 * definition. {@code matcherSpec} and {@code preconditions} hold JSON as text; the domain mapping into
 * the typed {@code MatchExpr} / {@code TraversalStrategy} model happens above this layer so the
 * persistence module stays free of the profile-guardian domain types.
 */
public record GuardianGuard(
        String guardId,
        String name,
        boolean enabled,
        boolean builtIn,
        String eventType,
        String category,
        String resultType,
        String targetFrame,
        String matchingType,
        double infoThreshold,
        double warningThreshold,
        long minSamples,
        String matcherSpec,
        String preconditions,
        String summaryNoun,
        String explanation,
        String solution,
        Instant createdAt) {
}
