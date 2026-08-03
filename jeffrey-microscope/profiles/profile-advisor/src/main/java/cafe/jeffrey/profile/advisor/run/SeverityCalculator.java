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

import cafe.jeffrey.shared.common.model.Severity;

/**
 * Grades a profile's findings from the measured share of the dominant hotspot.
 *
 * <p>This used to be a rule written into the system prompt and evaluated by the model, which made the
 * Overview ranking non-reproducible — the same recording could grade differently on two runs, and an
 * unparseable answer silently became {@code MEDIUM}. The rule is arithmetic over a number Jeffrey
 * already measured, so it belongs in Java.</p>
 *
 * <p>The input is a frame's <em>self</em> share, not its total share. Total share is dominated by
 * orchestration frames near the root, which sit at nearly 100% by construction and would grade every
 * profile {@code CRITICAL}. Self share is where time is actually spent, and it is what a code change
 * can move.</p>
 */
public final class SeverityCalculator {

    private static final double CRITICAL_THRESHOLD_PCT = 20.0;
    private static final double HIGH_THRESHOLD_PCT = 10.0;
    private static final double MEDIUM_THRESHOLD_PCT = 3.0;

    private SeverityCalculator() {
    }

    public static Severity fromDominantSharePct(double dominantSharePct) {
        if (dominantSharePct >= CRITICAL_THRESHOLD_PCT) {
            return Severity.CRITICAL;
        }
        if (dominantSharePct >= HIGH_THRESHOLD_PCT) {
            return Severity.HIGH;
        }
        if (dominantSharePct >= MEDIUM_THRESHOLD_PCT) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }
}
