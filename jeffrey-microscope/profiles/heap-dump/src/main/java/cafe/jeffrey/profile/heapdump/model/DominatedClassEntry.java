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
