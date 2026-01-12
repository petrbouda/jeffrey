/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Complete report for thread analysis in a heap dump including retained heap sizes.
 * The retained heap calculation is expensive, so results are pre-computed and stored.
 *
 * @param totalThreads      total Thread instances in the heap
 * @param daemonThreads     number of daemon threads
 * @param userThreads       number of user (non-daemon) threads
 * @param totalRetainedSize total retained heap size of all threads in bytes
 * @param threads           list of thread information with retained sizes
 */
public record ThreadAnalysisReport(
        int totalThreads,
        int daemonThreads,
        int userThreads,
        long totalRetainedSize,
        List<HeapThreadInfo> threads
) {
}
