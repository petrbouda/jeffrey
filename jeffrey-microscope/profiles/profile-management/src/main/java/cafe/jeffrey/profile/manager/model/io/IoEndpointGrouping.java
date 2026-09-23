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

package cafe.jeffrey.profile.manager.model.io;

import cafe.jeffrey.provider.profile.api.GenericRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates I/O events into per-target totals and hands them back ranked. The grouping key is the
 * caller's business — socket peer, file path or parent directory — so the same aggregation serves
 * Top Peers, Files and the per-peer timelines.
 */
final class IoEndpointGrouping {

    private static final Comparator<IoEndpoint> BY_BYTES =
            Comparator.comparingLong(IoEndpoint::bytes).reversed();
    private static final Comparator<IoEndpoint> BY_OP_COUNT =
            Comparator.comparingLong(IoEndpoint::opCount).reversed();

    private final Map<String, IoEndpointAccumulator> accumulatorsByTarget = new HashMap<>();

    void record(String target, GenericRecord record) {
        accumulatorsByTarget.computeIfAbsent(target, key -> new IoEndpointAccumulator()).record(record);
    }

    /**
     * Every target seen so far, heaviest first.
     */
    List<IoEndpoint> rankedByBytes() {
        return ranked(BY_BYTES);
    }

    /**
     * Every target seen so far, busiest first. A different order than {@link #rankedByBytes()}, and
     * once a caller truncates to a top-N also a different <em>set</em>: a chatty endpoint moving
     * almost nothing places highly here and nowhere at all there.
     */
    List<IoEndpoint> rankedByOpCount() {
        return ranked(BY_OP_COUNT);
    }

    List<IoEndpoint> rankedBy(IoMetric metric) {
        return metric == IoMetric.COUNT ? rankedByOpCount() : rankedByBytes();
    }

    private List<IoEndpoint> ranked(Comparator<IoEndpoint> comparator) {
        List<IoEndpoint> result = new ArrayList<>(accumulatorsByTarget.size());
        accumulatorsByTarget.forEach((target, accumulator) -> result.add(accumulator.toEndpoint(target)));
        result.sort(comparator);
        return result;
    }
}
