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

package cafe.jeffrey.profile.heapdump.sanitizer.strategy;

import cafe.jeffrey.profile.heapdump.sanitizer.HprofRepair;

import java.util.List;

/**
 * Result of consulting a {@link RepairStrategy}.
 */
public sealed interface StrategyOutcome {

    /** Singleton "this strategy doesn't apply" outcome. */
    NotApplicable NOT_APPLICABLE = new NotApplicable();

    /**
     * The strategy doesn't apply to the current scan state.
     */
    record NotApplicable() implements StrategyOutcome {
    }

    /**
     * The strategy detected a corruption pattern and produced repair operations.
     *
     * @param repairs       ordered list of repair operations to apply (left-to-right)
     * @param nextPosition  absolute offset to continue scanning from (ignored if {@code terminal})
     * @param terminal      if true, the planner stops scanning further records
     * @param objectsRecovered estimated count of recovered sub-records (for diagnostics)
     * @param description   human-readable description of what was repaired
     */
    record Applied(
            List<HprofRepair> repairs,
            long nextPosition,
            boolean terminal,
            long objectsRecovered,
            String description) implements StrategyOutcome {

        public Applied {
            repairs = List.copyOf(repairs);
            if (nextPosition < 0) {
                throw new IllegalArgumentException("nextPosition must be non-negative: nextPosition=" + nextPosition);
            }
            if (objectsRecovered < 0) {
                throw new IllegalArgumentException(
                        "objectsRecovered must be non-negative: objectsRecovered=" + objectsRecovered);
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description must be non-blank");
            }
        }
    }

    static StrategyOutcome notApplicable() {
        return NOT_APPLICABLE;
    }
}
