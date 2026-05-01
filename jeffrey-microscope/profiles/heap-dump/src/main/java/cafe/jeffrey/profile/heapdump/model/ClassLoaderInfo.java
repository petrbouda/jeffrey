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
 * Summary information about a single class loader instance.
 *
 * @param objectId              the object ID of the class loader instance (0 for bootstrap class loader)
 * @param classLoaderClassName  the class name of the class loader (e.g. "java.net.URLClassLoader")
 * @param classCount            number of classes loaded by this class loader
 * @param totalClassSize        total shallow size of all classes loaded by this class loader
 * @param retainedSize          retained size of the class loader instance (0 if not computed)
 */
public record ClassLoaderInfo(
        long objectId,
        String classLoaderClassName,
        int classCount,
        long totalClassSize,
        long retainedSize
) {
}
