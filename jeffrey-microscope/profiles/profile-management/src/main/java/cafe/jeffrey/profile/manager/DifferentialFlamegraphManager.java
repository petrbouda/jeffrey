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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.config.GraphParameters;

/**
 * A {@link FlamegraphManager} over a <em>pair</em> of profiles.
 * <p>
 * Everything the interface already promises still holds — {@code generate} draws the differential
 * flamegraph the browser renders, {@code eventSummaries} lists what the two profiles have in common —
 * and {@code generateAiExport} renders the diff as a readable call tree rather than refusing.
 * <p>
 * The one addition is {@link #rankedMovements}, which exists because a diff tree is a poor first read.
 * Pruned to any threshold it is still mostly frames that did not move, with the two or three that did
 * scattered through it at whatever depth they live; the ranked list puts them on the first line and
 * attributes each to the method that actually changed rather than to all of its callers.
 */
public interface DifferentialFlamegraphManager extends FlamegraphManager {

    /**
     * The methods that moved between the two profiles, ranked by how much work moved with them, as a
     * Markdown document.
     *
     * @param graphParameters what to compare: event type, time window and filters
     * @param limit           how many movements to report in each direction
     * @return Markdown suitable for handing to a model
     */
    String rankedMovements(GraphParameters graphParameters, int limit);
}
