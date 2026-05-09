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
 * A single leak suspect identified by dominator-cluster analysis.
 *
 * @param rank                  rank by leak score (1 = most suspicious)
 * @param className             fully qualified class name of the cluster root
 * @param objectId              object ID of the cluster root (the dominator-tree root of the suspect)
 * @param retainedSize          retained size of the cluster in bytes
 * @param heapPercentage        percentage of total heap retained by this cluster
 * @param instanceCount         total number of instances of {@code className} in the heap
 * @param reason                human-readable reason this cluster was flagged
 * @param accumulationPoint     human-readable description of where memory accumulates
 *                              (e.g. "HashMap.table holding 50,000 entries")
 * @param pathSteps             reference chain to the accumulation point (populated by classloader-leak-chain analysis)
 * @param accumulationPointId   object ID of the deepest dominator still ≥ cluster threshold
 * @param accumulationPointClass class name of the accumulation point
 * @param dominatedHistogram    top classes by retained size inside the cluster subtree
 * @param leakScore             {@code heapPercentage × penalty(className)} — used for ranking
 * @param classLoaderId         object ID of the class loader that defined {@code className} (0 for bootstrap)
 * @param classLoaderClassName  class name of that class loader (e.g. {@code "<bootstrap>"} for bootstrap)
 */
public record LeakSuspect(
        int rank,
        String className,
        Long objectId,
        long retainedSize,
        double heapPercentage,
        int instanceCount,
        String reason,
        String accumulationPoint,
        List<PathStep> pathSteps,
        Long accumulationPointId,
        String accumulationPointClass,
        List<DominatedClassEntry> dominatedHistogram,
        double leakScore,
        long classLoaderId,
        String classLoaderClassName
) {
}
