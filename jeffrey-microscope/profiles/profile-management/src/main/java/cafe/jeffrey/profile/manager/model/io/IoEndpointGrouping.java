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

package cafe.jeffrey.profile.manager.model.io;

import cafe.jeffrey.provider.profile.api.GenericRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates I/O events into per-target totals and hands them back ranked by bytes. The grouping key
 * is the caller's business — socket peer, file path or parent directory — so the same aggregation
 * serves Top Peers, Files and the per-peer timelines.
 */
final class IoEndpointGrouping {

    private final Map<String, IoEndpointAccumulator> accumulatorsByTarget = new HashMap<>();

    void record(String target, GenericRecord record) {
        accumulatorsByTarget.computeIfAbsent(target, key -> new IoEndpointAccumulator()).record(record);
    }

    /**
     * Every target seen so far, heaviest first.
     */
    List<IoEndpoint> rankedByBytes() {
        List<IoEndpoint> result = new ArrayList<>(accumulatorsByTarget.size());
        accumulatorsByTarget.forEach((target, accumulator) -> result.add(accumulator.toEndpoint(target)));
        result.sort(Comparator.comparingLong(IoEndpoint::bytes).reversed());
        return result;
    }
}
