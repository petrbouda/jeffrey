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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Inclusive range of localhost ports scanned to discover running IDE plugin instances.
 */
public record PortRange(int start, int end) {

    public PortRange {
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("Port values must be non-negative");
        }
        if (start > end) {
            throw new IllegalArgumentException("Port start must be less than or equal to port end");
        }
    }
}
