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

import java.util.HashMap;
import java.util.Map;

/**
 * The share of a profile spent in its single heaviest method, as a percentage of total samples. This is
 * the one number the Advisor keeps from the parsed call tree, and it is what severity is graded from —
 * so the ranking of a recording is arithmetic over what Jeffrey measured, never something the model was
 * asked to judge.
 *
 * <p>Two choices decide what the number means. <strong>Self samples are summed</strong> across every
 * call path a method appears on: self weight never nests inside itself, so summing it is exact even
 * under recursion, and it answers "how much time is actually spent in this method". And it is
 * <strong>self rather than total</strong>, because total share is dominated by orchestration frames
 * near the root, which sit at nearly 100% by construction and would grade every profile critical. Self
 * share is where time is actually spent, and it is what a code change can move.</p>
 */
public final class DominantSelfShare {

    private DominantSelfShare() {
    }

    /**
     * The heaviest method's self share of {@code root}, or {@code 0.0} for a tree with no samples.
     */
    public static double of(Frame root) {
        long totalSamples = root.totalSamples();
        if (totalSamples <= 0) {
            return 0.0;
        }

        Map<String, Long> selfByName = new HashMap<>();
        collect(root, selfByName);

        long dominant = selfByName.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        return 100.0 * dominant / totalSamples;
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
