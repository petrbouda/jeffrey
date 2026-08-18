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

package cafe.jeffrey.profile.manager.model.trace;

import java.util.List;

/**
 * What is different about a selection of traces — every key/value ranked by how much more common it
 * is inside the selection than outside it.
 * <p>
 * Exact, not estimated. A live tracer samples, so its version of this carries a confidence interval;
 * a recording is a bounded population, so these are the real counts of the real traces and the lift
 * is arithmetic.
 *
 * @param differences     the ranked values
 * @param selectionTraces how many traces the selection holds
 * @param baselineTraces  how many it does not — the two together are the profile
 */
public record TraceAttributeDifferences(
        List<Row> differences,
        long selectionTraces,
        long baselineTraces) {

    /**
     * One value, and how much more of the selection carries it than of the baseline.
     * <p>
     * The raw counts travel beside the shares on purpose: a share of 100% reads as damning until you
     * see it is 100% of three traces, and a ranking that shows only the percentage invites exactly
     * that mistake.
     *
     * @param lift the selection's share over the baseline's; a baseline share of zero is floored at
     *             one trace's worth, so a value unique to the selection ranks high rather than at
     *             infinity
     */
    public record Row(
            String source,
            String owner,
            String key,
            String value,
            long selectionTraces,
            long baselineTraces,
            double selectionShare,
            double baselineShare,
            double lift) {
    }
}
