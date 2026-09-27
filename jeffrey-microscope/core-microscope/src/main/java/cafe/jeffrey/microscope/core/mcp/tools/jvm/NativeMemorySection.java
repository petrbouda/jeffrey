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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryTrackingManager;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.profile.mcp.McpFollowUp;

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
public record NativeMemorySection(ProfileManager profileManager) implements JvmSection<NativeMemorySection.NativeMemoryDashboard> {

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

    private static final String NATIVE_FLAMEGRAPHS_WHY =
            "says whether the recording carries profiler.Malloc or jeffrey.NativeLeak, the native allocation "
                    + "flamegraphs; memory outside the Java heap does not appear in a heap dump";
    private static final String UNTRACKED_GUIDANCE =
            "A large untrackedBytes means something outside the JVM's own allocators holds the memory, "
                    + "which NMT cannot attribute.";

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
    public MicroscopeView view() {
        return MicroscopeView.NATIVE_MEMORY;
    }

    @Override
    public void followUp(NextSteps.Builder next, NativeMemoryDashboard dashboard) {
        next.next(SectionCalls.on(SectionCalls.FLAMEGRAPH_LIST, profileManager.info().id()).why(NATIVE_FLAMEGRAPHS_WHY))
                .guidanceWhen(dashboard.tracking() != null, UNTRACKED_GUIDANCE);
    }

    @Override
    public NativeMemoryDashboard render() {
        NativeMemoryManager memoryManager = profileManager.nativeMemoryManager();
        NativeMemoryTrackingManager trackingManager = profileManager.nativeMemoryTrackingManager();

        NativeMemoryOverview overview = memoryManager.overview();
        NmtOverview nmt = trackingManager.overview();

        List<NmtCategory> tracked = nmt.hasNmtData() ? trackingManager.categories() : List.of();
        return new NativeMemoryDashboard(
                process(overview),
                nmt.hasNmtData() ? tracking(nmt) : null,
                categories(tracked),
                Math.max(0, tracked.size() - CATEGORIES_LIMIT));
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
    public record NativeMemoryDashboard(
            Process process,
            @McpNullable
            Tracking tracking,
            @McpDescription("The " + CATEGORIES_LIMIT + " categories with the most committed memory")
            List<Category> categories,
            @McpDescription("How many further tracked categories the list leaves out")
            int omittedCategories) {
    }

    /**
     * @param rssGrowthBytes resident set size at the end minus the beginning — the number that says
     *                       whether the process was still growing when the recording stopped
     */
    public record Process(
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
    public record Tracking(
            long totalCommittedBytes,
            long totalReservedBytes,
            long peakCommittedBytes,
            @McpNullable
            String largestCategory,
            long largestCategoryCommittedBytes,
            int categoryCount,
            long untrackedBytes) {
    }

    public record Category(
            String category,
            long reservedBytes,
            long committedBytes,
            long startCommittedBytes,
            long growthBytes) {
    }

    /**
     * What {@code jvm_nativeMemory} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            NativeMemoryDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, NativeMemoryDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
