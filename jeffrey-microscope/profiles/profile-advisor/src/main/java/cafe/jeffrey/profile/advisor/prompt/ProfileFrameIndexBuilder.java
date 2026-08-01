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

package cafe.jeffrey.profile.advisor.prompt;

import cafe.jeffrey.frameir.Frame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens a {@link Frame} tree into the {@link ProfileFrameIndex} cached beside the prompt markdown.
 *
 * <p>Two different aggregations are applied deliberately, because the naive one is wrong in both
 * directions. <strong>Self samples are summed</strong> across every call path a method appears on:
 * self weight never nests inside itself, so summing it is exact even under recursion, and it answers
 * "how much time is actually spent in this method". <strong>Total samples take the maximum</strong>
 * across occurrences rather than the sum: a recursive method's subtree contains its own subtree, so
 * summing totals would count the same samples repeatedly.</p>
 *
 * <p>Frames below the same prune threshold used for the markdown are dropped, so the index describes
 * exactly the profile the model was shown — no more, no less. Grounding a claim against a frame the
 * model could not have seen would be as misleading as grounding it against nothing.</p>
 */
public final class ProfileFrameIndexBuilder {

    private ProfileFrameIndexBuilder() {
    }

    public static ProfileFrameIndex build(Frame root, double minThresholdPct) {
        long totalSamples = root.totalSamples();
        if (totalSamples <= 0) {
            return ProfileFrameIndex.empty();
        }

        long minSamples = (long) (totalSamples * minThresholdPct / 100.0);
        Map<String, Aggregate> byName = new HashMap<>();
        collect(root, minSamples, byName);

        List<ProfileFrame> frames = new ArrayList<>(byName.size());
        for (Map.Entry<String, Aggregate> entry : byName.entrySet()) {
            Aggregate aggregate = entry.getValue();
            frames.add(new ProfileFrame(
                    entry.getKey(),
                    percentOf(aggregate.maxTotalSamples, totalSamples),
                    percentOf(aggregate.summedSelfSamples, totalSamples),
                    aggregate.maxTotalSamples,
                    aggregate.interpretedSamples,
                    aggregate.c1Samples,
                    aggregate.c2Samples));
        }

        frames.sort(Comparator.comparingDouble(ProfileFrame::selfPct).reversed());
        return new ProfileFrameIndex(frames);
    }

    private static void collect(Frame parent, long minSamples, Map<String, Aggregate> byName) {
        for (Map.Entry<String, Frame> entry : parent.entrySet()) {
            Frame child = entry.getValue();
            if (child.totalSamples() < minSamples) {
                continue;
            }
            byName.computeIfAbsent(entry.getKey(), __ -> new Aggregate()).accept(child);
            collect(child, minSamples, byName);
        }
    }

    private static double percentOf(long part, long whole) {
        return whole <= 0 ? 0.0 : 100.0 * part / whole;
    }

    /**
     * Mutable accumulator used only while walking the tree; the immutable {@link ProfileFrame} is built
     * from it once the walk completes.
     */
    private static final class Aggregate {

        private long maxTotalSamples;
        private long summedSelfSamples;
        private long interpretedSamples;
        private long c1Samples;
        private long c2Samples;

        /**
         * Tier counts are taken from the heaviest occurrence rather than summed, for the same
         * recursion-safety reason total samples are — and because the tier question ("is this method
         * being promoted to C2?") is about the method, not about one call path into it.
         */
        private void accept(Frame frame) {
            this.summedSelfSamples += frame.selfSamples();
            if (frame.totalSamples() <= this.maxTotalSamples) {
                return;
            }
            this.maxTotalSamples = frame.totalSamples();
            this.interpretedSamples = frame.interpretedSamples();
            this.c1Samples = frame.c1Samples();
            this.c2Samples = frame.jitCompiledSamples();
        }
    }
}
