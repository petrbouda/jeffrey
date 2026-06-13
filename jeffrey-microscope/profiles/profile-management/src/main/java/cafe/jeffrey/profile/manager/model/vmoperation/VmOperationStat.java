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

package cafe.jeffrey.profile.manager.model.vmoperation;

/**
 * Aggregated statistics for one kind of VM operation from {@code jdk.ExecuteVMOperation}.
 *
 * @param operation  operation name (e.g. {@code G1CollectForAllocation}, {@code RevokeBias})
 * @param count      number of times this operation executed
 * @param totalNanos summed execution time
 * @param maxNanos   longest single execution
 * @param safepoint  whether this operation runs at a safepoint
 * @param blocking   whether the requesting thread blocks until the operation completes
 */
public record VmOperationStat(
        String operation,
        long count,
        long totalNanos,
        long maxNanos,
        boolean safepoint,
        boolean blocking) {
}
