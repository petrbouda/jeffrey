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

import java.util.List;

/**
 * Diagnostic record explaining why a suspicious class loader is still alive in the heap.
 * A leak chain combines:
 * <ul>
 *   <li>The class loader's identity and footprint (id, name, class count, retained size).</li>
 *   <li>A GC-root reference path showing what's keeping it alive
 *       (or {@code null} if no path could be computed).</li>
 *   <li>A list of {@link CauseHint}s annotating well-known leak patterns matched along the path.</li>
 * </ul>
 *
 * @param classLoaderId        object id of the class loader instance
 * @param classLoaderClassName class name of the loader (e.g. {@code "org.apache.catalina.loader.WebappClassLoader"})
 * @param classCount           number of classes loaded by this loader
 * @param totalClassSize       sum of shallow sizes of all classes loaded by this loader
 * @param retainedSize         retained size of the class loader instance
 * @param gcRootPath           shortest reference chain from a GC root to this loader, or {@code null} if none found
 * @param causeHints           heuristic annotations of leak patterns matched in the path
 * @param hasDuplicateClasses  {@code true} if at least one class loaded by this loader is also loaded by another loader
 *                             (typical of redeploy-leak scenarios)
 */
public record ClassLoaderLeakChain(
        long classLoaderId,
        String classLoaderClassName,
        int classCount,
        long totalClassSize,
        long retainedSize,
        GCRootPath gcRootPath,
        List<CauseHint> causeHints,
        boolean hasDuplicateClasses
) {
}
