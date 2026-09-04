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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryTrackingManager;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Set;

/**
 * The Native Memory dashboard: the process's memory outside the Java heap.
 * <p>
 * This is the half of a memory problem that neither a flamegraph nor a heap dump can see. "The
 * container was OOM-killed and the heap looked fine" is resident set size growing while the heap does
 * not — thread stacks, code cache, metaspace, direct byte buffers, a native library allocating on its
 * own. The tracked categories come from Native Memory Tracking, which the JVM only reports when it was
 * started with {@code -XX:NativeMemoryTracking=summary} or {@code detail}; RSS and direct buffers are
 * there regardless.
 */
public record NativeMemorySection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "nativeMemory";

    private static final String TITLE = "Native Memory";

    /** Tracked categories carried back, largest first. Beyond this the tail is noise. */
    private static final int CATEGORIES_LIMIT = 20;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.NATIVE_MEMORY_USAGE,
            Type.NATIVE_MEMORY_USAGE_TOTAL,
            Type.RESIDENT_SET_SIZE,
            Type.DIRECT_BUFFER_STATISTICS,
            Type.NATIVE_LIBRARY);

    private static final List<String> NEXT_STEPS = List.of(
            "Memory outside the Java heap does not appear in a heap dump. The native allocation "
                    + "flamegraphs are profiler.Malloc and jeffrey.NativeLeak when the recording carries them; "
                    + "flamegraph_list says whether it does.",
            "A large untracked figure means something outside the JVM's own allocators holds the memory, "
                    + "which NMT cannot attribute for you.");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public List<String> nextSteps() {
        return NEXT_STEPS;
    }

    @Override
    public Object render() {
        NativeMemoryManager memoryManager = profileManager.nativeMemoryManager();
        NativeMemoryTrackingManager trackingManager = profileManager.nativeMemoryTrackingManager();

        NativeMemoryOverview overview = memoryManager.overview();
        NmtOverview nmt = trackingManager.overview();

        return new NativeMemoryDashboard(
                process(overview),
                nmt.hasNmtData() ? tracking(nmt) : null,
                nmt.hasNmtData() ? categories(trackingManager.categories()) : List.of());
    }

    private static Process process(NativeMemoryOverview overview) {
        return new Process(
                overview.peakRssBytes(),
                overview.finalRssBytes(),
                overview.rssGrowthBytes(),
                overview.directBufferCount(),
                overview.directBufferMemoryUsed(),
                overview.directBufferTotalCapacity(),
                overview.nativeLibraryCount());
    }

    private static Tracking tracking(NmtOverview nmt) {
        return new Tracking(
                nmt.totalCommittedBytes(),
                nmt.totalReservedBytes(),
                nmt.peakCommittedBytes(),
                nmt.largestCategory(),
                nmt.largestCategoryCommittedBytes(),
                nmt.categoryCount(),
                nmt.untrackedBytes());
    }

    private static List<Category> categories(List<NmtCategory> categories) {
        return categories.stream()
                .limit(CATEGORIES_LIMIT)
                .map(category -> new Category(
                        category.category(),
                        category.reservedBytes(),
                        category.committedBytes(),
                        category.startCommittedBytes(),
                        category.growthBytes()))
                .toList();
    }

    /**
     * @param process    what the operating system and the JVM report about the whole process,
     *                   available in any recording that carries the events
     * @param tracking   the Native Memory Tracking totals, null when the JVM was started without NMT
     * @param categories where the tracked memory went, largest committed first; empty without NMT
     */
    private record NativeMemoryDashboard(
            Process process,
            Tracking tracking,
            List<Category> categories) {
    }

    /**
     * @param rssGrowthBytes resident set size at the end minus the beginning — the number that says
     *                       whether the process was still growing when the recording stopped
     */
    private record Process(
            long peakRssBytes,
            long finalRssBytes,
            long rssGrowthBytes,
            long directBufferCount,
            long directBufferUsedBytes,
            long directBufferCapacityBytes,
            int nativeLibraryCount) {
    }

    /**
     * @param untrackedBytes resident memory NMT does not account for — a large value is the signal
     *                       that something outside the JVM's own allocators holds the memory
     */
    private record Tracking(
            long totalCommittedBytes,
            long totalReservedBytes,
            long peakCommittedBytes,
            String largestCategory,
            long largestCategoryCommittedBytes,
            int categoryCount,
            long untrackedBytes) {
    }

    private record Category(
            String category,
            long reservedBytes,
            long committedBytes,
            long startCommittedBytes,
            long growthBytes) {
    }
}
