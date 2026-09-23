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

/**
 * Aggregated stats for one class within a leak-suspect cluster's dominator subtree.
 *
 * @param className       fully qualified class name
 * @param instanceCount   number of instances of this class dominated by the cluster root
 * @param retainedSize    sum of retained sizes of those instances
 * @param percentOfCluster percentage of the cluster's total retained size held by this class
 */
public record DominatedClassEntry(
        String className,
        int instanceCount,
        long retainedSize,
        double percentOfCluster
) {
}
