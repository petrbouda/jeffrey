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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.ai.WeightContext;
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
