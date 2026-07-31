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

import cafe.jeffrey.profile.advisor.verify.PatchVerification;
import cafe.jeffrey.profile.ai.chat.TokenUsage;
import cafe.jeffrey.shared.common.model.Severity;

import java.util.List;

/**
 * The artifacts an advisor run produces, after each has been checked against evidence: the
 * {@code severity} Jeffrey computed from the measured profile, the human-readable
 * {@code recommendations} markdown (analysis + rationale, no diffs), an applicable {@code patch} (a
 * single unified diff that {@code git apply} can consume), the {@code claims} the report rests on with
 * their grounding verdicts, what Jeffrey established about the patch, and what the run consumed.
 *
 * <p>{@code patch} is {@code null} when the model proposed no concrete code edits.</p>
 */
public record AdvisorResult(
        Severity severity,
        String recommendations,
        String patch,
        List<GroundedClaim> claims,
        PatchVerification verification,
        TokenUsage usage) {

    public AdvisorResult {
        claims = claims == null ? List.of() : List.copyOf(claims);
        verification = verification == null ? PatchVerification.notAttempted() : verification;
        usage = usage == null ? TokenUsage.unknown() : usage;
    }

    public boolean hasPatch() {
        return patch != null && !patch.isBlank();
    }

    public long groundedClaimCount() {
        return claims.stream().filter(GroundedClaim::grounded).count();
    }
}
