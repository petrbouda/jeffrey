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
 * Top-consumer entry: total retained size grouped by ({@code packageName}, {@code classLoader}).
 *
 * @param packageName          package the classes belong to
 * @param classLoaderId        object id of the class loader that defined the classes (0 = bootstrap)
 * @param classLoaderClassName class name of the class loader
 * @param retainedSize         sum of retained sizes of all instances in this (package, loader) cell
 * @param shallowSize          sum of shallow sizes
 * @param classCount           distinct classes contributing to this cell
 * @param instanceCount        instance count across all classes in the cell
 */
public record ConsumerEntry(
        String packageName,
        long classLoaderId,
        String classLoaderClassName,
        long retainedSize,
        long shallowSize,
        int classCount,
        long instanceCount
) {
}
