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
