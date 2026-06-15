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

package cafe.jeffrey.profile.guardian.definition;

import java.util.List;

/**
 * Describes how the observed frames are reached once the anchor frame has been matched. Maps 1:1 onto
 * the {@code Traversable} implementations:
 * <ul>
 *   <li>{@link CurrentFrame} — the anchor frame itself is the observed frame (the common case).</li>
 *   <li>{@link Descend} — walk a chain of {@link Step}s down from the anchor (used by the GC guards).</li>
 * </ul>
 */
public sealed interface TraversalStrategy permits TraversalStrategy.CurrentFrame, TraversalStrategy.Descend {

    record CurrentFrame() implements TraversalStrategy {
    }

    record Descend(List<Step> steps) implements TraversalStrategy {
        public Descend {
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("Descend requires at least one step");
            }
            steps = List.copyOf(steps);
        }
    }

    /** A single navigation step within a {@link Descend} strategy. */
    sealed interface Step permits Step.ByName, Step.ByMatcher {

        record ByName(String frameName) implements Step {
            public ByName {
                if (frameName == null || frameName.isBlank()) {
                    throw new IllegalArgumentException("ByName requires a frame name");
                }
            }
        }

        record ByMatcher(MatchExpr base, MatchExpr target) implements Step {
            public ByMatcher {
                if (base == null || target == null) {
                    throw new IllegalArgumentException("ByMatcher requires both base and target expressions");
                }
            }
        }
    }

    TraversalStrategy CURRENT_FRAME = new CurrentFrame();
}
