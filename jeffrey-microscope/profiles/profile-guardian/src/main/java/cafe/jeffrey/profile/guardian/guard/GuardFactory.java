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

package cafe.jeffrey.profile.guardian.guard;

import cafe.jeffrey.profile.guardian.definition.GuardDefinition;
import cafe.jeffrey.profile.guardian.definition.GuardDefinitions;

import java.util.List;

/**
 * Instantiates the {@link ConfigurableGuard}s belonging to a group from the loaded
 * {@link GuardDefinitions}. Replaces the former hard-coded {@code GuardRegistry} enum — a guard is now
 * just a database row, so a new (or customized) guard requires no code change.
 */
public final class GuardFactory {

    private GuardFactory() {
    }

    /** Builds every enabled guard registered for {@code kind}, bound to the given profile. */
    public static List<Guard> instantiateFor(
            GroupKind kind, Guard.ProfileInfo profileInfo, GuardDefinitions definitions) {

        return definitions.forGroup(kind).stream()
                .map(definition -> (Guard) new ConfigurableGuard(profileInfo, definition))
                .toList();
    }
}
