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
 * Coarse classification of a class loader by its declaring class. Drives the
 * type badge on the Class Loaders page and the "hide JDK loaders" toolbar
 * filter on the Hierarchy tab.
 */
public enum LoaderType {
    BOOTSTRAP,
    PLATFORM,
    SYSTEM,
    WEB,
    OSGI,
    APP,
    CUSTOM
}
