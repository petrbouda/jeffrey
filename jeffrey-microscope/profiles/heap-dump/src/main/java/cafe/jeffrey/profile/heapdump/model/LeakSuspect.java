/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
