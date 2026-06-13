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

package cafe.jeffrey.profile.manager.model.classloading;

/**
 * A single class load, derived from a {@code jdk.ClassLoad} event.
 *
 * @param className            binary name of the loaded class
 * @param durationNanos        time spent loading the class, in nanoseconds
 * @param definingClassLoader  loader that defined the class, or {@code null} for the bootstrap loader
 */
public record ClassLoadEntry(
        String className,
        long durationNanos,
        String definingClassLoader) {
}
