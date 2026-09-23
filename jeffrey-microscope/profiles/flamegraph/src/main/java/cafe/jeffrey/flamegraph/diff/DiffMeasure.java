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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.export.WeightContext;
import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.frameir.Frame;

/**
 * Reads one side's measurement out of a diff node, in whichever unit the event type is weighed by.
 * <p>
 * Shared by the ranking walk and the tree renderer so the two cannot drift apart. A node that exists
 * on only one side is the subtle part: it holds a plain {@link Frame} rather than a primary/baseline
 * pair, and it contributes nothing at all to the side it is missing from — which is exactly what makes
 * an appeared or vanished call path fall in full into the delta. Getting that backwards in one of the
 * two walks would have the ranked list and the tree disagree about the same recording.
 */
final class DiffMeasure {

    private final boolean weighted;

    DiffMeasure(WeightContext weightContext) {
        this.weighted = weightContext.weighted();
    }

    long primary(DiffFrame node) {
        return switch (node.type) {
            case SHARED -> weighted ? node.primaryWeight : node.primarySamples;
            case ADDED -> total(node.frame);
            case REMOVED -> 0L;
        };
    }

    long baseline(DiffFrame node) {
        return switch (node.type) {
            case SHARED -> weighted ? node.secondaryWeight : node.secondarySamples;
            case ADDED -> 0L;
            case REMOVED -> total(node.frame);
        };
    }

    long total(Frame frame) {
        return weighted ? frame.totalWeight() : frame.totalSamples();
    }

    long self(Frame frame) {
        return weighted ? frame.selfWeight() : frame.selfSamples();
    }
}
