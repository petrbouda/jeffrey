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
 * Supplies the effective guard definitions that drive a Guardian run. Implemented in core-microscope
 * by reading the central database, keeping the {@code profile-guardian} module free of any persistence
 * dependency. Guardian groups the definitions by their {@link GuardDefinition#eventType()} at runtime.
 */
public interface GuardDefinitions {

    /** All enabled guard definitions, across every event type. */
    List<GuardDefinition> all();
}
