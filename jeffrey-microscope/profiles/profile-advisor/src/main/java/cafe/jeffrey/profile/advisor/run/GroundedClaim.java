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

import cafe.jeffrey.profile.advisor.prompt.ProfileFrame;

/**
 * A {@link AdvisorClaim} after it has been checked against the evidence: the measured frame it
 * resolved to (null when the cited frame was never sampled) and whether the source file it named exists
 * in the checkout.
 *
 * <p>An ungrounded claim is kept rather than dropped. Silently deleting a model's mistake hides the
 * failure from the person who most needs to see it; showing it, clearly marked and excluded from
 * severity, is both more honest and more useful.</p>
 *
 * @param claim       the citation as the model made it
 * @param frame       the measured frame it resolved to, or null when the profile contains no such frame
 * @param sourceFound true when the cited source path exists in the analyzed checkout
 */
public record GroundedClaim(AdvisorClaim claim, ProfileFrame frame, boolean sourceFound) {

    public boolean grounded() {
        return frame != null;
    }

    public static GroundedClaim ungrounded(AdvisorClaim claim) {
        return new GroundedClaim(claim, null, false);
    }
}
