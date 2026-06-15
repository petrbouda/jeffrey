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

import cafe.jeffrey.profile.guardian.guard.GroupKind;

import java.util.List;

/**
 * Supplies the effective guard definitions and the per-group minimum-sample gates that drive a
 * Guardian run. Implemented in core-microscope by reading the central database, keeping the
 * {@code profile-guardian} module free of any persistence dependency.
 */
public interface GuardDefinitions {

    /** All enabled guard definitions, across every group. */
    List<GuardDefinition> all();

    /** Enabled guard definitions belonging to the given group. */
    default List<GuardDefinition> forGroup(GroupKind group) {
        return all().stream()
                .filter(definition -> definition.group() == group)
                .toList();
    }

    /** Minimum number of samples a group must have before its guards run. */
    long minSamples(GroupKind group);
}
