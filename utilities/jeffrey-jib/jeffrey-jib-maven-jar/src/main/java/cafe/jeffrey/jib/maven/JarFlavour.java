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

package cafe.jeffrey.jib.maven;

/**
 * Marks the jar flavour of the Jeffrey JIB extension for Maven: the extension plus a payload
 * jar carrying the architecture-neutral provisioner jar and async-profiler.
 *
 * <p>This class carries no behaviour. It exists so the module publishes a non-empty sources and
 * javadoc jar, which Maven Central requires of every artifact; the module is its two dependencies.
 */
public abstract class JarFlavour {
}
