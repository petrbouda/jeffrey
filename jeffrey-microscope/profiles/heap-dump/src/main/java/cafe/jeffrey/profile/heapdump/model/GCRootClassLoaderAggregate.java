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
 * One row of the "Roots by ClassLoader" view: how many GC roots reference
 * classes loaded by a given classloader, and what they retain.
 *
 * @param classloaderObjectId object id of the classloader instance, or
 *                            {@code null} for the bootstrap loader
 * @param classloaderClass    fully qualified class name of the loader, or
 *                            {@code "Bootstrap"} when {@code classloaderObjectId}
 *                            is {@code null}
 * @param rootCount           number of GC root rows whose rooted class was
 *                            loaded by this loader
 * @param totalRetainedBytes  sum of retained sizes across all those roots
 */
public record GCRootClassLoaderAggregate(
        Long classloaderObjectId,
        String classloaderClass,
        long rootCount,
        long totalRetainedBytes
) {
}
