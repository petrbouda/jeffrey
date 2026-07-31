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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.performance.analyst.persistence.StoredClaim;

import java.util.List;

/**
 * One checked citation as the UI shows it: what the model claimed, and what Jeffrey could confirm. The
 * measured shares are only meaningful when {@code grounded} is true; the UI uses that flag to decide
 * whether to present the claim as evidence or as an unsubstantiated assertion.
 */
public record ClaimResponse(
        String title,
        String citedFrame,
        String sourcePath,
        boolean grounded,
        boolean sourceFound,
        double selfPct,
        double totalPct) {

    public static List<ClaimResponse> from(List<GroundedClaim> claims) {
        return claims.stream().map(ClaimResponse::from).toList();
    }

    public static ClaimResponse from(GroundedClaim claim) {
        return new ClaimResponse(
                claim.claim().title(),
                claim.grounded() ? claim.frame().name() : claim.claim().citedFrame(),
                claim.claim().sourcePath(),
                claim.grounded(),
                claim.sourceFound(),
                claim.grounded() ? claim.frame().selfPct() : 0.0,
                claim.grounded() ? claim.frame().totalPct() : 0.0);
    }

    public static ClaimResponse from(StoredClaim stored) {
        return new ClaimResponse(
                stored.title(),
                stored.citedFrame(),
                stored.sourcePath(),
                stored.grounded(),
                stored.sourceFound(),
                stored.selfPct(),
                stored.totalPct());
    }
}
