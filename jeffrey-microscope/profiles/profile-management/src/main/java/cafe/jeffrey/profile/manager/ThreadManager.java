/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.provider.profile.api.AllocatingThread;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.List;
import java.util.function.Function;

public interface ThreadManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ThreadManager> {
    }

    ThreadStats threadStatistics();

    SingleSerie activeThreadsSerie();

    List<AllocatingThread> threadsAllocatingMemory(int limit);

    Type resolveAllocationType();

    ThreadCpuLoads threadCpuLoads(int limit);

    ThreadRoot threadRows();

    /**
     * Cross-dump analysis of all {@code jdk.ThreadDump} occurrences (state timeline, top frames,
     * deadlocks, lock contention, stuck threads, heatmap). Excludes per-thread stacks — fetch those
     * per dump via {@link #threadDump(int)}.
     */
    ThreadDumpAnalysis threadDumpAnalysis();

    /**
     * The fully parsed thread dump at {@code index} (its threads + stacks + raw text), for the dump
     * viewer. Returns an empty dump when the index is out of range.
     */
    ParsedDump threadDump(int index);
}
