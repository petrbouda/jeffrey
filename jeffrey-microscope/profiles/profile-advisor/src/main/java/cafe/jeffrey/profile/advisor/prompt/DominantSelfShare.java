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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * The single heaviest method of a profile and its share of it, as a percentage of total samples. The
 * share is what severity is graded from — so the ranking of a recording is arithmetic over what Jeffrey
 * measured, never something the model was asked to judge — and the method's name is carried out with it,
 * because a grade that names the frame it came from is one a reader can act on.
 *
 * <p>Two choices decide what the number means. <strong>Self samples are summed</strong> across every
 * call path a method appears on: self weight never nests inside itself, so summing it is exact even
 * under recursion, and it answers "how much time is actually spent in this method". And it is
 * <strong>self rather than total</strong>, because total share is dominated by orchestration frames
 * near the root, which sit at nearly 100% by construction and would grade every profile critical. Self
 * share is where time is actually spent, and it is what a code change can move.</p>
 */
public final class DominantSelfShare {

    /**
     * Heaviest first, and on a tie the alphabetically first name — a walk over a hash map has no order
     * of its own, so without a tie-break two equally hot methods would name whichever one the map
     * happened to hand back.
     */
    private static final Comparator<Map.Entry<String, Long>> BY_WEIGHT_THEN_NAME =
            Map.Entry.<String, Long>comparingByValue()
                    .thenComparing(Map.Entry.<String, Long>comparingByKey(Comparator.reverseOrder()));

    private DominantSelfShare() {
    }

    /**
     * The heaviest method of {@code root} and its self share, or {@link DominantMethod#NONE} for a tree
     * with no samples. Ties are broken by method name so the same tree always names the same frame.
     */
    public static DominantMethod of(Frame root) {
        long totalSamples = root.totalSamples();
        if (totalSamples <= 0) {
            return DominantMethod.NONE;
        }

        Map<String, Long> selfByName = new HashMap<>();
        collect(root, selfByName);

        return selfByName.entrySet().stream()
                .max(BY_WEIGHT_THEN_NAME)
                .map(entry -> new DominantMethod(entry.getKey(), 100.0 * entry.getValue() / totalSamples))
                .orElse(DominantMethod.NONE);
    }

    /**
     * Walks the tree accumulating self samples per method name. The root is not itself a method — it is
     * the synthetic node the call tree hangs from — so only its descendants are counted.
     */
    private static void collect(Frame parent, Map<String, Long> selfByName) {
        for (Map.Entry<String, Frame> entry : parent.entrySet()) {
            Frame child = entry.getValue();
            selfByName.merge(entry.getKey(), child.selfSamples(), Long::sum);
            collect(child, selfByName);
        }
    }
}
