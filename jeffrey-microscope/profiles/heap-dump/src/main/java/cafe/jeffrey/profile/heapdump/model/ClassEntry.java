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

package cafe.jeffrey.profile.heapdump.model;

/**
 * One class defined by a class loader, with its instance footprint. Renders
 * as a row in the Classes sub-tab of the Loader Detail drawer.
 *
 * <p>Intentionally omits per-class retained size: summing
 * {@code retained_size.bytes} across all instances of one class double-counts
 * any dominator subtree that nests another instance of the same class
 * (tries, linked lists, trees), routinely producing totals larger than the
 * whole heap. Per-loader retained size — which has a single, well-defined
 * meaning — is reported in the drawer header instead.
 */
public record ClassEntry(
        long classId,
        String name,
        long instanceCount,
        long totalInstanceSize) {
}
