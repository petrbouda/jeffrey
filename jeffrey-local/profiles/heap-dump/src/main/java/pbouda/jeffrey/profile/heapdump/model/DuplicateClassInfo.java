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

package pbouda.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Information about a class that is loaded by multiple class loaders,
 * which may indicate a class loader leak or misconfiguration.
 *
 * @param className        the fully qualified class name loaded by multiple loaders
 * @param loaderCount      number of distinct class loaders that loaded this class
 * @param classLoaderNames list of class loader class names that loaded this class
 */
public record DuplicateClassInfo(
        String className,
        int loaderCount,
        List<String> classLoaderNames
) {
}
