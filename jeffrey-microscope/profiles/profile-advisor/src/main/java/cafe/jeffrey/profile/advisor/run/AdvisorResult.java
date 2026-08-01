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

package cafe.jeffrey.profile.advisor.run;

import cafe.jeffrey.profile.ai.chat.TokenUsage;
import cafe.jeffrey.shared.common.model.Severity;

import java.util.List;

/**
 * The artifacts an advisor run produces, after each has been grounded against evidence: the
 * {@code severity} Jeffrey computed from the measured profile, the human-readable
 * {@code recommendations} markdown (analysis + rationale, no diffs), the {@code claims} the report
 * rests on with their grounding verdicts, and what the run consumed.
 */
public record AdvisorResult(
        Severity severity,
        String recommendations,
        List<GroundedClaim> claims,
        TokenUsage usage) {

    public AdvisorResult {
        claims = claims == null ? List.of() : List.copyOf(claims);
        usage = usage == null ? TokenUsage.unknown() : usage;
    }

    public long groundedClaimCount() {
        return claims.stream().filter(GroundedClaim::grounded).count();
    }
}
