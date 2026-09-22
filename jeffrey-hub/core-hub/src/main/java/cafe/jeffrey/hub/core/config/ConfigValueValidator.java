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


package cafe.jeffrey.hub.core.config;

/**
 * Checks one configuration type's value before it is stored.
 *
 * <p>One validator per type, looked up rather than branched on, so a type cannot be added to the
 * catalogue without someone deciding what a valid value for it is.</p>
 */
@FunctionalInterface
public interface ConfigValueValidator {

    /**
     * @throws IllegalArgumentException with a message naming the rule that failed, which the gRPC
     *                                  boundary turns into INVALID_ARGUMENT and the UI shows as is
     */
    void validate(String value);
}
